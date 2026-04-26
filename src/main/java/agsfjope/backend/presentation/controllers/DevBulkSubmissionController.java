package agsfjope.backend.presentation.controllers;

import agsfjope.backend.application.dtos.responses.submission.SubmissionResponse;
import agsfjope.backend.application.submissionservices.SubmissionService;
import agsfjope.backend.core.entities.Submission;
import agsfjope.backend.core.entities.User;
import agsfjope.backend.core.repositories.appeal.AppealRepository;
import agsfjope.backend.core.repositories.auth.UserRepository;
import agsfjope.backend.core.repositories.submission.SubmissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import java.util.stream.Collectors;

/**
 * <strong>DEV / TEST ONLY</strong> — Bulk-upload submissions for multiple students.
 *
 * <p>Upload N files → system auto-picks N students (STUDENT role) and assigns
 * each file to a different student. Students whose existing submissions have
 * an appeal are automatically skipped to avoid FK constraint errors.</p>
 *
 * <h3>How to use:</h3>
 * <pre>
 * POST /api/dev/exams/{examId}/blocks/{blockId}/bulk-submit
 * Content-Type: multipart/form-data
 *
 * files = [bai1.zip, bai2.zip, bai3.zip, ...]
 * </pre>
 *
 * <p><strong>No authentication required</strong> — endpoint is open (permitAll).</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/dev")
@RequiredArgsConstructor
public class DevBulkSubmissionController {

    private final SubmissionService    submissionService;
    private final UserRepository       userRepository;
    private final SubmissionRepository submissionRepository;
    private final AppealRepository     appealRepository;

    /**
     * Bulk-submit files for multiple students at once.
     *
     * <p>Upload N files → system auto-picks N eligible students and submits.
     * Students with existing submissions that have appeals are skipped.</p>
     */
    @PostMapping(value = "/exams/{examId}/blocks/{blockId}/bulk-submit",
                 consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> bulkSubmit(
            @PathVariable UUID examId,
            @PathVariable UUID blockId,
            @RequestPart("files") MultipartFile[] files
    ) {
        log.info("DevBulkSubmit: START — examId={}, blockId={}", examId, blockId);

        // ── 0. Handle Zip-of-Zips (Master Zip) ───────────────────────────────
        List<MultipartFile> finalFileList = new ArrayList<>();
        if (files.length == 1 && isZipFile(files[0])) {
            log.info("DevBulkSubmit: Detected single ZIP file. Checking for Master Zip content...");
            try {
                List<MultipartFile> extracted = extractNestedArchives(files[0]);
                if (!extracted.isEmpty()) {
                    log.info("DevBulkSubmit: Successfully extracted {} internal archives from Master Zip.", extracted.size());
                    finalFileList.addAll(extracted);
                } else {
                    log.warn("DevBulkSubmit: ZIP uploaded but no internal archives found. Treating as single submission.");
                    finalFileList.add(files[0]);
                }
            } catch (IOException e) {
                log.error("DevBulkSubmit: Failed to process Master Zip: {}", e.getMessage());
                return ResponseEntity.badRequest().body(buildErrorResponse("Lỗi khi xử lý file nén tổng: " + e.getMessage()));
            }
        } else {
            finalFileList.addAll(Arrays.asList(files));
        }

        // Sort by name for deterministic ordering (matches Excel report)
        finalFileList.sort(Comparator.comparing(f -> f.getOriginalFilename() != null ? f.getOriginalFilename() : ""));

        log.info("DevBulkSubmit: Final count of submissions to process: {}", finalFileList.size());

        // ── 1. Fetch all students ────────────────────────────────────────────
        List<User> allStudents = userRepository.findByRole_NameAndDeletedAtIsNull("STUDENT");
        if (allStudents.isEmpty()) {
            return ResponseEntity.badRequest().body(buildErrorResponse(
                    "Không tìm thấy student nào trong hệ thống. Hãy tạo tài khoản student trước."));
        }

        // ── 2. Filter out students whose existing submission has an appeal ───
        // Collect submissionIds that have appeals
        Set<UUID> submissionIdsWithAppeal = allStudents.stream()
                .map(s -> submissionRepository.findByStudent_UserIdAndBlock_BlockId(s.getUserId(), blockId))
                .filter(Optional::isPresent)
                .map(opt -> opt.get().getSubmissionId())
                .filter(subId -> appealRepository.existsBySubmission_SubmissionId(subId))
                .collect(Collectors.toSet());

        // Build set of studentIds to skip
        Set<UUID> studentIdsToSkip = allStudents.stream()
                .filter(s -> {
                    Optional<Submission> sub = submissionRepository
                            .findByStudent_UserIdAndBlock_BlockId(s.getUserId(), blockId);
                    return sub.isPresent() && submissionIdsWithAppeal.contains(sub.get().getSubmissionId());
                })
                .map(User::getUserId)
                .collect(Collectors.toSet());

        List<User> eligibleStudents = allStudents.stream()
                .filter(s -> !studentIdsToSkip.contains(s.getUserId()))
                .toList();

        log.info("DevBulkSubmit: {} total students, {} skipped (have appeal), {} eligible.",
                allStudents.size(), studentIdsToSkip.size(), eligibleStudents.size());

        if (eligibleStudents.isEmpty()) {
            return ResponseEntity.badRequest().body(buildErrorResponse(
                    "Tất cả students đều có submission kèm appeal. Không thể resubmit."));
        }

        // ── 3. Assign each file to an eligible student and submit ────────────
        List<Map<String, Object>> results = new ArrayList<>();
        int successCount = 0;

        for (int i = 0; i < finalFileList.size(); i++) {
            Map<String, Object> item = new LinkedHashMap<>();
            MultipartFile file = finalFileList.get(i);
            item.put("index", i + 1);
            item.put("fileName", file.getOriginalFilename());

            if (i >= eligibleStudents.size()) {
                item.put("mssv", null);
                item.put("studentName", null);
                item.put("success", false);
                item.put("submissionId", null);
                item.put("message", "Không đủ student eligible — file " + file.getOriginalFilename() +
                        " bị bỏ qua (chỉ có " + eligibleStudents.size() + " students khả dụng).");
                results.add(item);
                continue;
            }

            User student = eligibleStudents.get(i);
            item.put("mssv", student.getMssv());
            item.put("studentName", student.getFullName());

            try {
                SubmissionResponse response = submissionService.submitSkipTimeCheck(
                        examId, blockId, student.getUserId(), file);

                item.put("success", true);
                item.put("submissionId", response.getSubmissionId());
                item.put("totalAnswers", response.getTotalAnswers());
                item.put("resubmit", response.isResubmit());
                item.put("message", response.isResubmit()
                        ? "Nộp lại thành công (đã ghi đè bài cũ)."
                        : "Nộp bài thành công.");
                successCount++;
            } catch (Exception e) {
                log.error("DevBulkSubmit: Failed for student {} (MSSV={}): {}",
                        student.getFullName(), student.getMssv(), e.getMessage());
                item.put("success", false);
                item.put("submissionId", null);
                item.put("message", "Lỗi: " + e.getMessage());
            }

            results.add(item);
        }

        // ── 4. Build summary response ────────────────────────────────────────
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("totalFilesProcessed", finalFileList.size());
        data.put("totalStudentsInSystem", allStudents.size());
        data.put("skippedWithAppeal", studentIdsToSkip.size());
        data.put("eligibleStudents", eligibleStudents.size());
        data.put("successCount", successCount);
        data.put("failedCount", finalFileList.size() - successCount);
        data.put("results", results);

        String msg = String.format("Bulk upload hoàn tất: %d/%d thành công.", successCount, finalFileList.size());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", successCount > 0);
        response.put("message", msg);
        response.put("data", data);
        response.put("errors", null);

        log.info("DevBulkSubmit: Done — {}/{} succeeded.", successCount, files.length);
        return ResponseEntity.ok(response);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private Map<String, Object> buildErrorResponse(String message) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", false);
        response.put("message", message);
        response.put("data", null);
        response.put("errors", List.of(message));
        return response;
    }

    private boolean isZipFile(MultipartFile file) {
        String name = file.getOriginalFilename();
        return name != null && name.toLowerCase().endsWith(".zip");
    }

    /**
     * Extracts submission units from a master ZIP file.
     * <p>A submission unit can be:</p>
     * <ul>
     *     <li>A nested archive (.zip, .rar, .7z)</li>
     *     <li>A top-level folder containing source code (will be zipped automatically)</li>
     * </ul>
     */
    private List<MultipartFile> extractNestedArchives(MultipartFile masterZip) throws IOException {
        List<MultipartFile> finalFiles = new ArrayList<>();
        Map<String, ByteArrayOutputStream> folderZips = new LinkedHashMap<>();
        Map<String, ZipOutputStream> zosMap = new HashMap<>();

        try (ZipInputStream zis = new ZipInputStream(masterZip.getInputStream())) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;

                String name = entry.getName();
                String lower = name.toLowerCase();

                // Skip MacOS metadata and common junk files
                if (name.contains("__MACOSX") || name.contains(".DS_Store") || name.contains("desktop.ini")) {
                    zis.closeEntry();
                    continue;
                }

                if (lower.endsWith(".zip") || lower.endsWith(".rar") || lower.endsWith(".7z")) {
                    // Case 1: Nested archive - extract as a standalone submission
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = zis.read(buffer)) > 0) {
                        bos.write(buffer, 0, len);
                    }
                    String fileName = new File(name).getName();
                    finalFiles.add(new ZipEntryMultipartFile(fileName, bos.toByteArray()));
                } else {
                    // Case 2: Direct file - group by top-level folder name
                    String[] parts = name.split("[/\\\\]");
                    if (parts.length > 1) {
                        String folderName = parts[0];
                        
                        folderZips.computeIfAbsent(folderName, k -> new ByteArrayOutputStream());
                        ZipOutputStream zos = zosMap.computeIfAbsent(folderName, k -> new ZipOutputStream(folderZips.get(k)));

                        // Add the file to the folder's zip archive
                        // Strip the top-level folder name from the entry path
                        String internalPath = String.join("/", Arrays.copyOfRange(parts, 1, parts.length));
                        zos.putNextEntry(new ZipEntry(internalPath));
                        
                        byte[] buffer = new byte[8192];
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            zos.write(buffer, 0, len);
                        }
                        zos.closeEntry();
                    }
                }
                zis.closeEntry();
            }
        }

        // Close all active ZipOutputStreams and convert to MultipartFiles
        for (Map.Entry<String, ZipOutputStream> entry : zosMap.entrySet()) {
            entry.getValue().close();
            String folderName = entry.getKey();
            byte[] bytes = folderZips.get(folderName).toByteArray();
            finalFiles.add(new ZipEntryMultipartFile(folderName + ".zip", bytes));
        }

        return finalFiles;
    }

    /**
     * Simple inner class to wrap extracted zip bytes as a MultipartFile.
     */
    @RequiredArgsConstructor
    private static class ZipEntryMultipartFile implements MultipartFile {
        private final String fileName;
        private final byte[] content;

        @Override public String getName() { return "files"; }
        @Override public String getOriginalFilename() { return fileName; }
        @Override public String getContentType() { return "application/zip"; }
        @Override public boolean isEmpty() { return content == null || content.length == 0; }
        @Override public long getSize() { return content.length; }
        @Override public byte[] getBytes() throws IOException { return content; }
        @Override public InputStream getInputStream() throws IOException { return new ByteArrayInputStream(content); }
        @Override public void transferTo(File dest) throws IOException, IllegalStateException { Files.write(dest.toPath(), content); }
    }
}
