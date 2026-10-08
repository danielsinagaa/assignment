# 08 — SUBMISSION CHECKLIST

## Isi zip (root)
- [ ] `README.md` — 1 perintah run · stack · cara client retry create dengan aman (Idempotency-Key: dikirim apa, dibuat bagaimana) · asumsi · **AI tools (3 baris)** · yang tidak diimplementasi
- [ ] `DESIGN.md` — Q1, Q2, Q3 (+ Q4, Q5 kalau Part 2), masing-masing **≤ 150 kata** (hitung!)
- [ ] `schema.sql` — CREATE TABLE, index, constraint, seed customers
- [ ] `RESULT.md` — S1–S6 (+ S7–S9): request lengkap **dengan header**, status, body. Dari app yang baru start.
- [ ] `src/` · `pom.xml` · `mvnw` · `mvnw.cmd` · `.mvn/`
- [ ] `test-output.txt` (kalau ada test)
- [ ] `scripts/scenarios.sh` (boleh ikut, membantu reviewer mereproduksi)

## JANGAN ikut
- [ ] `target/`, `.git/`, `.idea/`, `*.iml`, `.vscode/`
- [ ] `notes/`, `CLAUDE.md`, file .docx brief

## Verifikasi akhir (di folder hasil unzip yang bersih)
- [ ] Unzip ke folder baru → `mvnw.cmd spring-boot:run` jalan dengan JDK 17
- [ ] Jalankan `scripts/scenarios.sh` → hasilnya sama dengan RESULT.md
- [ ] `mvnw.cmd test` hijau
- [ ] Tidak ada TODO/kode mati/komentar yang tidak bisa Anda jelaskan
- [ ] README: perintah run bisa di-copy-paste apa adanya
- [ ] Semua asumsi di 02-DECISIONS yang terlihat oleh reviewer sudah tercantum di README
