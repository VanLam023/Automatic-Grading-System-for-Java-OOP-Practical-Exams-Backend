package agsfjope.backend.infrastructure.storage.parser;

import agsfjope.backend.core.exceptions.exampaper.InvalidZipStructureException;
import net.sf.sevenzipjbinding.ExtractOperationResult;
import net.sf.sevenzipjbinding.IInArchive;
import net.sf.sevenzipjbinding.ISequentialOutStream;
import net.sf.sevenzipjbinding.SevenZip;
import net.sf.sevenzipjbinding.SevenZipException;
import net.sf.sevenzipjbinding.impl.RandomAccessFileInStream;
import net.sf.sevenzipjbinding.simple.ISimpleInArchive;
import net.sf.sevenzipjbinding.simple.ISimpleInArchiveItem;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Utility for reading RAR archives (RAR4 and RAR5) using sevenzipjbinding
 * (JNI wrapper for 7-Zip). Supports both RAR generations without any
 * external tool installation.
 *
 * <p>Usage patterns:
 * <ul>
 *   <li>{@link #readAllEntries(Path)} — loads entire archive into memory (for small archives)</li>
 *   <li>{@link #iterateEntries(Path, BiConsumer)} — streams entries one by one (for large archives)</li>
 * </ul>
 */
public final class RarExtractor {

    private RarExtractor() {}

    /**
     * Reads all entries from a RAR archive and returns them as a map.
     *
     * @param rarPath path to the .rar file
     * @return map of (normalized entry name → raw byte content); directory entries have empty byte[]
     * @throws InvalidZipStructureException if the archive cannot be opened or is corrupt
     */
    public static Map<String, byte[]> readAllEntries(Path rarPath) throws IOException {
        Map<String, byte[]> result = new LinkedHashMap<>();
        iterateEntries(rarPath, result::put);
        return result;
    }

    /**
     * Iterates over all entries in a RAR archive, calling {@code consumer} for each one.
     * Directories are passed with an empty byte array.
     *
     * @param rarPath  path to the .rar file
     * @param consumer receives (normalizedName, rawBytes) for every entry
     * @throws InvalidZipStructureException on read errors
     */
    public static void iterateEntries(Path rarPath, BiConsumer<String, byte[]> consumer) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(rarPath.toFile(), "r");
             IInArchive archive = SevenZip.openInArchive(null, new RandomAccessFileInStream(raf))) {

            ISimpleInArchive simple = archive.getSimpleInterface();
            for (ISimpleInArchiveItem item : simple.getArchiveItems()) {
                String name = normalizeSlash(item.getPath());
                if (item.isFolder()) {
                    consumer.accept(name, new byte[0]);
                    continue;
                }

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ExtractOperationResult res = item.extractSlow(data -> {
                    try {
                        baos.write(data);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    return data.length;
                });

                if (res != ExtractOperationResult.OK) {
                    throw new InvalidZipStructureException(
                            "Không thể giải nén entry '" + name + "': " + res);
                }
                consumer.accept(name, baos.toByteArray());
            }
        } catch (InvalidZipStructureException e) {
            throw e;
        } catch (SevenZipException e) {
            throw new InvalidZipStructureException(
                    "Không thể đọc file .rar. Hãy đảm bảo file không bị hỏng: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new InvalidZipStructureException(
                    "Lỗi khi đọc file .rar: " + e.getMessage(), e);
        }
    }

    /**
     * Extracts a single matching entry from a RAR archive directly to a byte array.
     * Returns {@code null} if no entry matches the predicate.
     *
     * @param rarPath   path to the .rar file
     * @param predicate returns true for the entry name you want to extract
     * @return byte content of the first matched entry, or null if not found
     */
    public static byte[] extractFirstMatching(Path rarPath,
                                              java.util.function.Predicate<String> predicate) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(rarPath.toFile(), "r");
             IInArchive archive = SevenZip.openInArchive(null, new RandomAccessFileInStream(raf))) {

            ISimpleInArchive simple = archive.getSimpleInterface();
            for (ISimpleInArchiveItem item : simple.getArchiveItems()) {
                if (item.isFolder()) continue;
                String name = normalizeSlash(item.getPath());
                if (!predicate.test(name)) continue;

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ExtractOperationResult res = item.extractSlow(data -> {
                    try {
                        baos.write(data);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    return data.length;
                });

                if (res != ExtractOperationResult.OK) {
                    throw new InvalidZipStructureException(
                            "Không thể giải nén entry '" + name + "': " + res);
                }
                return baos.toByteArray();
            }
        } catch (InvalidZipStructureException e) {
            throw e;
        } catch (SevenZipException e) {
            throw new InvalidZipStructureException(
                    "Không thể đọc file .rar: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new InvalidZipStructureException(
                    "Lỗi khi đọc file .rar: " + e.getMessage(), e);
        }
        return null;
    }

    private static String normalizeSlash(String path) {
        return path == null ? "" : path.replace('\\', '/');
    }
}
