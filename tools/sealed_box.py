#!/usr/bin/env python3
"""
sealed_box.py — GitHub Actions secrets এনক্রিপ্ট করার জন্য NaCl sealed box (libsodium)।

GitHub Actions secrets PUT API (`PUT /repos/{owner}/{repo}/actions/secrets/{name}`) চায়
`encrypted_value` যা রিপো পাবলিক কী দিয়ে **libsodium sealed box** দিয়ে এনক্রিপ্ট করা।

Termux-এ `pynacl` ইনস্টল না থাকলে (Rust দরকার হয়) এই মডিউলটি libsodium-এর
`crypto_box_seal` অ্যালগরিদমটি pure Python দিয়ে বাস্তবায়ন করে:

    ephemeral_keypair  = crypto_box_keypair()
    nonce              = blake2b(ephemeral_pk || recipient_pk, digest_size=24)
    shared_secret      = crypto_box_beforenm(ephemeral_sk, recipient_pk)      # X25519 + HSalsa20
    boxed              = crypto_secretbox(message, nonce, shared_secret)      # XSalsa20-Poly1305
    sealed             = ephemeral_pk || boxed

এটি libsodium-এর `crypto_box_seal` স্পেসিফিকেশনের হুবহু অনুকরণ, তাই GitHub যাচাই করতে পারে।
"""

from __future__ import annotations

import hashlib
import os
import struct

# ---------------------------------------------------------------------------
# X25519 (Curve25519 ECDH) — RFC 7748
# ---------------------------------------------------------------------------

P = 2**255 - 19
A24 = 121665


def _decode_scalar(k: bytes) -> int:
    b = bytearray(k)
    b[0] &= 248
    b[31] &= 127
    b[31] |= 64
    return int.from_bytes(bytes(b), "little")


def _x25519(k: bytes, u: bytes) -> bytes:
    """X25519 scalar multiplication (RFC 7748 Montgomery ladder)."""
    k_int = _decode_scalar(k)
    x1 = int.from_bytes(u, "little") & ((1 << 255) - 1)

    x2, z2 = 1, 0
    x3, z3 = x1, 1
    swap = 0

    for t in reversed(range(255)):
        k_t = (k_int >> t) & 1
        swap ^= k_t
        if swap:
            x2, x3 = x3, x2
            z2, z3 = z3, z2
        swap = k_t

        a = (x2 + z2) % P
        aa = (a * a) % P
        b = (x2 - z2) % P
        bb = (b * b) % P
        e = (aa - bb) % P
        c = (x3 + z3) % P
        d = (x3 - z3) % P
        da = (d * a) % P
        cb = (c * b) % P
        x3 = pow((da + cb) % P, 2, P)
        z3 = (x1 * pow((da - cb) % P, 2, P)) % P
        x2 = (aa * bb) % P
        z2 = (e * ((aa + A24 * e) % P)) % P

    if swap:
        x2, x3 = x3, x2
        z2, z3 = z3, z2

    result = (x2 * pow(z2, P - 2, P)) % P
    return result.to_bytes(32, "little")


BASE_POINT = (9).to_bytes(32, "little")


def x25519_base(scalar: bytes) -> bytes:
    return _x25519(scalar, BASE_POINT)


def x25519(scalar: bytes, point: bytes) -> bytes:
    return _x25519(scalar, point)


# ---------------------------------------------------------------------------
# HSalsa20 — libsodium এর crypto_core_hsalsa20 (beforenm ধাপে ব্যবহৃত)
# ---------------------------------------------------------------------------

_SIGMA = b"expand 32-byte k"


def _rotl32(v: int, c: int) -> int:
    return ((v << c) & 0xFFFFFFFF) | (v >> (32 - c))


def _quarterround(a: int, b: int, c: int, d: int):
    b ^= _rotl32((a + d) & 0xFFFFFFFF, 7)
    c ^= _rotl32((b + a) & 0xFFFFFFFF, 9)
    d ^= _rotl32((c + b) & 0xFFFFFFFF, 13)
    a ^= _rotl32((d + c) & 0xFFFFFFFF, 18)
    return a, b, c, d


def hsalsa20(key: bytes, nonce: bytes) -> bytes:
    """HSalsa20(key32, nonce16) -> 32-byte subkey (libsodium এর আগের ধাপ)।"""
    assert len(key) == 32 and len(nonce) == 16

    c = struct.unpack("<4I", _SIGMA)
    k = struct.unpack("<8I", key)
    n = struct.unpack("<4I", nonce)

    x = [
        c[0], k[0], k[1], k[2],
        k[3], c[1], n[0], n[1],
        n[2], n[3], c[2], k[4],
        k[5], k[6], k[7], c[3],
    ]

    # Salsa20 = 20 রাউন্ড = 10 ডাবল-রাউন্ড
    for _ in range(10):
        # column rounds
        x[0], x[4], x[8], x[12] = _quarterround(x[0], x[4], x[8], x[12])
        x[5], x[9], x[13], x[1] = _quarterround(x[5], x[9], x[13], x[1])
        x[10], x[14], x[2], x[6] = _quarterround(x[10], x[14], x[2], x[6])
        x[15], x[3], x[7], x[11] = _quarterround(x[15], x[3], x[7], x[11])
        # row rounds
        x[0], x[1], x[2], x[3] = _quarterround(x[0], x[1], x[2], x[3])
        x[5], x[6], x[7], x[4] = _quarterround(x[5], x[6], x[7], x[4])
        x[10], x[11], x[8], x[9] = _quarterround(x[10], x[11], x[8], x[9])
        x[15], x[12], x[13], x[14] = _quarterround(x[15], x[12], x[13], x[14])

    # HSalsa20 আউটপুট = x[0], x[5], x[10], x[15], x[6], x[7], x[8], x[9]
    out = [x[0], x[5], x[10], x[15], x[6], x[7], x[8], x[9]]
    return struct.pack("<8I", *[v & 0xFFFFFFFF for v in out])


# ---------------------------------------------------------------------------
# XSalsa20-Poly1305 (crypto_secretbox) — libsodium-compatible
# ---------------------------------------------------------------------------

_MASK = 0xFFFFFFFF


def _salsa20_block(key: bytes, nonce: bytes, counter: int) -> bytes:
    c = struct.unpack("<4I", _SIGMA)
    k = struct.unpack("<8I", key)
    n = struct.unpack("<2I", nonce)

    j = [
        c[0], k[0], k[1], k[2],
        k[3], c[1], n[0], n[1],
        counter & _MASK, (counter >> 32) & _MASK, c[2], k[4],
        k[5], k[6], k[7], c[3],
    ]
    x = list(j)

    for _ in range(10):
        x[4] ^= _rotl32((x[0] + x[12]) & _MASK, 7)
        x[8] ^= _rotl32((x[4] + x[0]) & _MASK, 9)
        x[12] ^= _rotl32((x[8] + x[4]) & _MASK, 13)
        x[0] ^= _rotl32((x[12] + x[8]) & _MASK, 18)

        x[9] ^= _rotl32((x[5] + x[1]) & _MASK, 7)
        x[13] ^= _rotl32((x[9] + x[5]) & _MASK, 9)
        x[1] ^= _rotl32((x[13] + x[9]) & _MASK, 13)
        x[5] ^= _rotl32((x[1] + x[13]) & _MASK, 18)

        x[14] ^= _rotl32((x[10] + x[6]) & _MASK, 7)
        x[2] ^= _rotl32((x[14] + x[10]) & _MASK, 9)
        x[6] ^= _rotl32((x[2] + x[14]) & _MASK, 13)
        x[10] ^= _rotl32((x[6] + x[2]) & _MASK, 18)

        x[3] ^= _rotl32((x[15] + x[11]) & _MASK, 7)
        x[7] ^= _rotl32((x[3] + x[15]) & _MASK, 9)
        x[11] ^= _rotl32((x[7] + x[3]) & _MASK, 13)
        x[15] ^= _rotl32((x[11] + x[7]) & _MASK, 18)

        x[1] ^= _rotl32((x[0] + x[3]) & _MASK, 7)
        x[2] ^= _rotl32((x[1] + x[0]) & _MASK, 9)
        x[3] ^= _rotl32((x[2] + x[1]) & _MASK, 13)
        x[0] ^= _rotl32((x[3] + x[2]) & _MASK, 18)

        x[6] ^= _rotl32((x[5] + x[4]) & _MASK, 7)
        x[7] ^= _rotl32((x[6] + x[5]) & _MASK, 9)
        x[4] ^= _rotl32((x[7] + x[6]) & _MASK, 13)
        x[5] ^= _rotl32((x[4] + x[7]) & _MASK, 18)

        x[11] ^= _rotl32((x[10] + x[9]) & _MASK, 7)
        x[8] ^= _rotl32((x[11] + x[10]) & _MASK, 9)
        x[9] ^= _rotl32((x[8] + x[11]) & _MASK, 13)
        x[10] ^= _rotl32((x[9] + x[8]) & _MASK, 18)

        x[12] ^= _rotl32((x[15] + x[14]) & _MASK, 7)
        x[13] ^= _rotl32((x[12] + x[15]) & _MASK, 9)
        x[14] ^= _rotl32((x[13] + x[12]) & _MASK, 13)
        x[15] ^= _rotl32((x[14] + x[13]) & _MASK, 18)

    return struct.pack("<16I", *[(x[i] + j[i]) & _MASK for i in range(16)])


def _xsalsa20_stream(key: bytes, nonce24: bytes, length: int) -> bytes:
    """XSalsa20 keystream: HSalsa20 দিয়ে subkey তৈরি করে Salsa20 চালায়।"""
    subkey = hsalsa20(key, nonce24[:16])
    nonce8 = nonce24[16:]
    out = bytearray()
    counter = 0
    while len(out) < length:
        out += _salsa20_block(subkey, nonce8, counter)
        counter += 1
    return bytes(out[:length])


def _poly1305(key: bytes, message: bytes) -> bytes:
    """Poly1305 one-time authenticator (32-byte key)।"""
    r = int.from_bytes(key[:16], "little") & 0x0FFFFFFC0FFFFFFC0FFFFFFC0FFFFFFF
    s = int.from_bytes(key[16:], "little")
    p = (1 << 130) - 5

    acc = 0
    for i in range(0, len(message), 16):
        chunk = message[i : i + 16]
        n = int.from_bytes(chunk + b"\x01", "little")
        acc = ((acc + n) * r) % p
    acc = (acc + s) & ((1 << 128) - 1)
    return acc.to_bytes(16, "little")


def secretbox(message: bytes, nonce: bytes, key: bytes) -> bytes:
    """crypto_secretbox: returns MAC(16) || ciphertext (libsodium প্যারিটি)।"""
    assert len(nonce) == 24 and len(key) == 32

    stream = _xsalsa20_stream(key, nonce, len(message) + 32)
    poly_key = stream[:32]
    cipher = bytes(a ^ b for a, b in zip(message, stream[32:]))
    tag = _poly1305(poly_key, cipher)
    return tag + cipher


# ---------------------------------------------------------------------------
# Public API — sealed box
# ---------------------------------------------------------------------------

def crypto_box_beforenm(pk: bytes, sk: bytes) -> bytes:
    """X25519 + HSalsa20 = 32-byte shared key (`crypto_box_beforenm`)।"""
    shared = x25519(sk, pk)
    return hsalsa20(shared, b"\x00" * 16)


def crypto_box_seal(message: bytes, recipient_pk: bytes) -> bytes:
    """libsodium `crypto_box_seal` — GitHub Actions secrets এনক্রিপশনের জন্য।"""
    assert len(recipient_pk) == 32

    ephemeral_sk = os.urandom(32)
    ephemeral_pk = x25519_base(ephemeral_sk)

    nonce = hashlib.blake2b(ephemeral_pk + recipient_pk, digest_size=24).digest()
    shared_key = crypto_box_beforenm(recipient_pk, ephemeral_sk)
    boxed = secretbox(message, nonce, shared_key)
    return ephemeral_pk + boxed


def seal_for_github(value: str, public_key_b64: str) -> str:
    """GitHub এর base64 public key দিয়ে value এনক্রিপ্ট করে base64 রিটার্ন করে।"""
    import base64

    pk = base64.b64decode(public_key_b64)
    sealed = crypto_box_seal(value.encode("utf-8"), pk)
    return base64.b64encode(sealed).decode("ascii")


if __name__ == "__main__":
    # সেলফ-টেস্ট: রাউন্ড-ট্রিপ যাচাই (sealed box নিজে ডিক্রিপ্ট করতে পারি কি না)
    import base64

    sk = os.urandom(32)
    pk = x25519_base(sk)
    msg = b"hello github secrets"
    sealed = crypto_box_seal(msg, pk)

    epk = sealed[:32]
    box = sealed[32:]
    nonce = hashlib.blake2b(epk + pk, digest_size=24).digest()
    shared = crypto_box_beforenm(epk, sk)
    stream = _xsalsa20_stream(shared, nonce, len(box) - 16 + 32)
    tag, cipher = box[:16], box[16:]
    opened = bytes(a ^ b for a, b in zip(cipher, stream[32:]))
    ok = _poly1305(stream[:32], cipher) == tag and opened == msg
    print("sealed box round-trip:", "OK" if ok else "FAILED")
    print("sample encrypted_value:", base64.b64encode(sealed).decode()[:44] + "...")