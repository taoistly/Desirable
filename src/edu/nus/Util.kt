package edu.nus

import java.io.File
import java.util.zip.GZIPInputStream
import me.tongfei.progressbar.ProgressBar
import java.io.FileInputStream
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.channels.Channels

object UtilString {
    val supl = mapOf<Char, Char>('A' to 'T', 'C' to 'G', 'G' to 'C', 'T' to 'A')
    fun reverse(kmer: String) = kmer.reversed().map { supl[it] }.joinToString(separator = "")
    fun canonical(kmer: String) = if (kmer[kmer.length / 2] in "AC") Pair(kmer, 0) else Pair(reverse(kmer), 1)
}

object Util {
    var K: Int = 0
    val decodeTable = arrayOf('A', 'C', 'T', 'G')
    fun encode(kmer: String): Long = kmer.mapIndexed { i, ch -> ch.toLong() ushr 1 and 3 shl (2 * K - 2 * (i + 1)) }.sum()
    fun decode(kmer: Long): String = (2 * K - 2 downTo 0 step 2).map { decodeTable[(kmer ushr it and 3).toInt()] }.joinToString(separator = "")
    fun reverse(kmer: Long): Long = (2..2 * K step 2).map { kmer ushr (2 * K - it) and 3 xor 2 shl (it - 2) }.sum()
    fun canonical(kmer: String): Long = canonical(encode(kmer)).first
    fun canonical(kmer: Long): Pair<Long, Int> {
        val rc = reverse(kmer);
        return if (kmer < rc) Pair(kmer, 0) else Pair(rc, 1)
    }
}

/* A sequence read file reader, automatically accept fasta/fastq with/without gz compression*/
class ReadFileReader(file: File, description: String) {
    val filename = file.absolutePath
    val format = if (filename.contains("fastq") || filename.contains("fq")) "fastq" else "fasta"
    val fileChannel = RandomAccessFile(file, "r").channel
    val inputStream = Channels.newInputStream(fileChannel)
    val reader = (if (filename.endsWith(".gz")) GZIPInputStream(inputStream) else inputStream).reader().buffered(1024 * 1024)
    val pb = ProgressBar(description, file.length())
    var readname: String? = null
    var seq: String? = null
    var qual: String? = null
    fun nextRead(): Boolean {
        if (!reader.ready()) {
            pb.close(); return false
        }
        readname = reader.readLine()
        seq = reader.readLine()
        if (format == "fastq") {
            reader.readLine()
            qual = reader.readLine()
        }
        pb.stepTo(fileChannel.position())
        return true
    }

    fun decomposeKmer(K: Int): List<Long> {
        val mod = (1L shl 2*K)-1
        var kmer = (1..K - 1).map { (seq!![it - 1].toLong() ushr 1 and 3) shl (2 * (K - 1 - it)) }.sum()
        return (K..seq!!.length).map {
            kmer = ((kmer shl 1) and mod) + (seq!![it - 1].toLong() ushr 1 and 3)
            Util.canonical(kmer).first
        }
    }
}

class BloomFilter(val K: Int, maxSize: Long, hashSeeds: List<Int>) {
    private val bitSet = Array<Long>((maxSize / 64 + 1).toInt(), { 0L })
    private val hashes = hashSeeds.map { seed ->
        { k: Long -> (0..K - 1).fold(0L, { hash, i -> (hash * seed + (k ushr (i * 2) and 3)) % maxSize }) }
    }

    init {
        if (maxSize > 64L * 2000000000) println("[Warning] BloomFilter size is too large!")
    }

    fun insert(k: Long) = hashes.map { it(k) }.forEach {
        bitSet[(it / 64).toInt()] = bitSet[(it / 64).toInt()] or (1L shl (it % 64).toInt())
    }

    fun contains(k: Long) = hashes.map { it(k) }.all { (bitSet[(it / 64).toInt()] and (1L shl (it % 64).toInt())) != 0L }
}