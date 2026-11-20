`TEST-ONLY-team-key.der.b64` is a throwaway key (base64 of DER) for tests. It protects nothing and must never be
used in a build or deployment. Its public half is `android/core/src/test/resources/test_team_key.pub.b64`.
`sealed_by_kotlin.bin` was sealed by the app's Seal.kt (SealTest) with that public key; the
Python tests open it, proving both sides agree on the format.
