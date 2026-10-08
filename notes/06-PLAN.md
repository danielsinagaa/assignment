# 06 — PLAN (checklist langkah kerja)

Tanggal mulai: 2026-09-30 · **Deadline (4 hari kalender): ____ (isi tanggal assignment diterima + 4)**
Aturan: centang `[x]` setelah langkah selesai **dan** diverifikasi. Jangan lompat ke Part 2 sebelum Fase 6 selesai.

## Fase 0 — Persiapan (sebelum ada kode)
- [x] Transkrip brief → `01-SPEC.md` (source of truth)
- [x] Keputusan desain → `02-DECISIONS.md`
- [x] Kontrak API → `03-API-CONTRACT.md`
- [x] Draft schema → `04-SCHEMA-DESIGN.md`
- [x] Ekspektasi skenario → `05-SCENARIOS.md`
- [ ] Anda baca & setujui 02-DECISIONS (tanya kalau ada yang tidak bisa Anda jelaskan sendiri)
- [ ] `git init` lokal (untuk riwayat kerja; `.git/` dihapus saat zip)
- [ ] (Opsional) Email ke Finshot soal kontradiksi PartnerClient (D-12)

## Fase 1 — Generate project Spring Boot (dilakukan Anda)
Di https://start.spring.io:
- Project **Maven** · Language **Java** · Spring Boot **4.1.1** (bukan SNAPSHOT/M)
- Group `com.finshot` · Artifact/Name `remittance` · Description `Remittance Transfer API` · Package `com.finshot.remittance`
- Packaging **Jar** · Configuration **YAML** · Java **17**
- Dependencies: **Spring Web, Validation, Spring Data JPA, H2 Database**
- Unzip isinya **langsung ke root** folder ini (`pom.xml`, `mvnw`, `mvnw.cmd`, `.mvn/`, `src/` sejajar dengan `notes/`)
- Hapus `HELP.md`
- [x] Cek: `mvnw.cmd spring-boot:run` jalan tanpa error

## Fase 2 — Fondasi
- [x] `/schema.sql` dari 04-SCHEMA-DESIGN (bagian Part 1 dulu; kolom Part 2 boleh langsung ikut)
- [x] pom: copy `schema.sql` ke classpath · `application.yml` (H2 mem, `ddl-auto=validate`, `sql.init`)
- [x] `ErrorResponse` + `GlobalExceptionHandler` + exception classes
- [x] `ClockConfig` (bean `Clock`)
- [x] Cek: app start, schema tervalidasi, URL asal → 404 dengan format error yang benar

## Fase 3 — Uang (paling mudah dites, kerjakan dulu)
- [x] `TransferQuote.of(sendAmount)` → fee, totalDebit, receiveAmount (D-06)
- [x] Parsing & validasi sendAmount (D-07)
- [x] `TransferQuoteTest` (D-18 #1) hijau

## Fase 4 — Create / Get / Cancel
- [x] Entity `Transfer`, `Customer`, repositories
- [x] `TransferStatus` + transisi (D-09), CAS update query
- [x] Controller + DTO + validasi (D-08), response format (D-16)
- [x] Cek manual: create, get, cancel, cancel lagi → 409

## Fase 5 — Idempotency
- [x] Header wajib, request hash, lock customer, lookup, replay 201 + header (D-04, D-05)
- [x] Tangkap unique violation → re-read → replay/409
- [x] `TransferConcurrencyTest` (idempotency + daily limit, D-18 #2/#3) hijau — dan terbukti GAGAL kalau lock dicabut

## Fase 6 — Bukti & dokumen Part 1 (±1 jam, JANGAN ditunda)
- [x] `scripts/scenarios.sh` S1–S6 → jalankan di app yang baru start → `RESULT.md`
- [ ] `DESIGN.md` Q1–Q3 (≤150 kata, bahasa Inggris sederhana) — tulis sendiri dari 02/04, saya bantu review
- [ ] `README.md`: 1 perintah run, stack, cara retry (Idempotency-Key), asumsi (dari 02), AI tools (3 baris, jujur), tidak diimplementasi (D-19)
- [x] `mvnw test` → `test-output.txt`
- [ ] **Checkpoint: Part 1 bisa dikumpulkan apa adanya.** Buat zip cadangan.

## Fase 7 — Part 2 (opsional)
- [x] `business_date` + daily limit dengan lock (D-13) + `DailyLimitConcurrencyTest`
- [x] `PartnerClient` + `SimulatedPartnerClient` + retry loop + log (D-12)
- [x] `/send` endpoint dengan Tx1 → call → Tx2 (D-11)
- [x] Callback + tabel event (D-14) + `CallbackIdempotencyTest`
- [x] S7–S9 ke script + RESULT.md (+ potongan log S9)
- [ ] DESIGN.md Q4, Q5 · update README (endpoint /send, asumsi D-11, D-12)

## Fase 8 — Pengumpulan
- [ ] Ikuti `08-SUBMISSION-CHECKLIST.md`

## Fase 9 — Persiapan interview
- [ ] Latihan semua soal di `07-INTERVIEW-PREP.md` tanpa melihat catatan
