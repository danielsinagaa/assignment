# 07 — INTERVIEW PREP

Di interview Anda akan share screen lalu diminta **menjelaskan, mengubah, dan menambah fitur** pada kode Anda sendiri secara live.
Kolom "Di mana di kode" diisi setelah kode jadi. Latih setiap soal sampai bisa menjawab tanpa membuka catatan.

## A. Pertanyaan "jelaskan" (kemungkinan besar ditanyakan)
| # | Pertanyaan | Inti jawaban | Di mana di kode |
|---|---|---|---|
| A1 | Kenapa tidak pakai `double` untuk uang? | Binary floating point tidak bisa merepresentasikan 0.0412 dengan tepat. BigDecimal + satu titik pembulatan (D-06) | |
| A2 | Kapan pembulatan terjadi? | Sekali, di `TransferQuote.of`, sebelum disimpan. Setelah itu nilainya tidak pernah dihitung ulang | |
| A3 | Dua retry datang bersamaan, apa yang terjadi? | Lock baris customer menyerialkan request; UNIQUE key jadi pengaman terakhir → yang kalah membaca ulang lalu replay (D-04) | |
| A4 | Kenapa Idempotency-Key global, bukan per customer? | Tidak ada auth, jadi customerId hanyalah data body (D-04) | |
| A5 | Kenapa replay mengembalikan 201? | Bagi client, retry harus terlihat identik; ada header `Idempotent-Replayed` (D-05) | |
| A6 | Bagaimana mencegah transisi state yang salah di bawah beban? | Compare-and-set `WHERE status = :from` (D-09) | |
| A7 | Kenapa panggilan ke partner tidak di dalam transaksi? | Transaksi panjang = lock dan koneksi DB tertahan ±1,2 detik. Tx1 commit dulu (D-11) | |
| A8 | Callback datang sebelum hasil panggilan partner disimpan? | SENDING sudah di-commit, jadi callback berhasil. Tx2 bersyarat, tidak menimpa COMPLETED (D-11 / Q5) | |
| A9 | Apa yang dikorbankan dengan row lock untuk daily limit? | Request dari customer yang sama diproses bergantian. Alternatifnya tabel counter (D-13) | |
| A10 | App crash setelah SENDING di-commit? | Transfer tertahan di SENDING → butuh recovery job / outbox (D-19) | |
| A11 | Kenapa aturan last digit, bukan hashCode? | Ada kontradiksi di brief; aturan yang di-bold + kalimat S9 (D-12) | |
| A12 | Bagaimana kalau dijalankan di 2 instance? | Lock dan constraint ada di DB, jadi tetap benar. Hanya H2 in-memory yang tidak bisa dipakai bersama → produksi pakai Postgres | |

## B. Latihan "ubah secara live" (latih benar-benar di kode)
| # | Permintaan | Yang disentuh |
|---|---|---|
| B1 | Fee minimum jadi 5.000 / fee 1,5% | `TransferQuote` + test |
| B2 | Kurs dipindah ke config / tambah mata uang baru (IDR) | konstanta FX, validasi currency, CHECK di schema |
| B3 | Daily limit dibuat per minggu / per bulan | query SUM + perhitungan tanggal |
| B4 | Tambah status baru `REFUNDED` dari COMPLETED | enum transisi + CHECK schema |
| B5 | Idempotency key kedaluwarsa setelah 24 jam | kolom created_at + kondisi lookup |
| B6 | Buang lock → tunjukkan test concurrency jadi gagal | `DailyLimitConcurrencyTest` |
| B7 | Retry partner jadi 5 kali dengan jitter | retry loop di `PartnerSender` |
| B8 | Tambah endpoint list transfer per customer | controller + index `ix_transfers_customer_day` |
| B9 | Callback menerima status FAILED | DTO validasi + transisi SENDING→FAILED |

## C. Hal yang harus bisa Anda tunjukkan cepat
- Menjalankan app dan salah satu skenario dari terminal.
- Menjalankan satu test tertentu: `mvnw.cmd test -Dtest=TransferQuoteTest`.
- Membuka H2 console (kalau diaktifkan) dan menunjukkan constraint yang bekerja.
