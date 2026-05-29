package agsfjope.backend.infrastructure.storage.parser;

import agsfjope.backend.core.exceptions.exampaper.InvalidZipStructureException;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ZipExamPaperParser Tests")
class ZipExamPaperParserTest {

    @Test
    @DisplayName("[A] parse tc - Throw khi testcase sau co INPUT nhung thieu OUTPUT")
    void parseTcFile_InputWithoutOutputAfterValidCase_ThrowsInvalidZipStructureException() throws Exception {
        ZipExamPaperParser parser = new ZipExamPaperParser();

        String content = """
                INPUT:
                1
                OUTPUT:
                one
                INPUT:
                2
                REMOVE_SPACES:
                YES
                CASE_SENSITIVE:
                YES
                """;

        assertThatThrownBy(() -> invokeParseTcFile(parser, content))
                .isInstanceOf(InvalidZipStructureException.class)
                .hasMessageContaining("thieu 'OUTPUT:'");
    }

    @Test
    @DisplayName("[A] parse tc - Throw khi gap OUTPUT lien tiep khong co INPUT moi")
    void parseTcFile_DuplicateOutputWithoutInput_ThrowsInvalidZipStructureException() throws Exception {
        ZipExamPaperParser parser = new ZipExamPaperParser();

        String content = """
                INPUT:
                Laptop
                electronics
                1
                OUTPUT:
                ELECTRONICS
                INPUT:
                Laptop
                electronics
                2
                OUTPUT:
                100.0
                INPUT:
                TShirt
                clothing
                2
                OUTPUT:
                200.0
                OUTPUT:
                TShirt,CLOTHING,200.0
                REMOVE_SPACES:
                NO
                CASE_SENSITIVE:
                YES
                """;

        assertThatThrownBy(() -> invokeParseTcFile(parser, content))
                .isInstanceOf(InvalidZipStructureException.class)
                .hasMessageContaining("OUTPUT")
                .hasMessageContaining("INPUT");
    }

    @Test
    @DisplayName("[A] parse tc - Throw khi gap INPUT lien tiep chua co OUTPUT")
    void parseTcFile_DuplicateInputWithoutOutput_ThrowsInvalidZipStructureException() throws Exception {
        ZipExamPaperParser parser = new ZipExamPaperParser();

        String content = """
                INPUT:
                Laptop
                electronics
                1
                INPUT:
                TShirt
                clothing
                2
                OUTPUT:
                200.0
                REMOVE_SPACES:
                NO
                CASE_SENSITIVE:
                YES
                """;

        assertThatThrownBy(() -> invokeParseTcFile(parser, content))
                .isInstanceOf(InvalidZipStructureException.class)
                .hasMessageContaining("INPUT")
                .hasMessageContaining("thieu 'OUTPUT:'");
    }

    @Test
    @DisplayName("[A] parse zip - Quet den question 2 va throw khi tc2 thieu OUTPUT")
    void parseFromTempFile_InvalidSecondQuestion_ThrowsInvalidZipStructureException() throws Exception {
        ZipExamPaperParser parser = new ZipExamPaperParser();
        Path zipPath = Files.createTempFile("exam-paper-all-questions-", ".zip");

        try {
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(zipPath))) {
                addEntry(zip, "Exam/1/Q1.docx", docxBytes("Question 1", "Score: 5"));
                addEntry(zip, "Exam/1/tc1.txt", """
                        INPUT:
                        1
                        OUTPUT:
                        one
                        REMOVE_SPACES:
                        YES
                        CASE_SENSITIVE:
                        YES
                        """.getBytes(StandardCharsets.UTF_8));

                addEntry(zip, "Exam/2/Q2.docx", docxBytes("Question 2", "Score: 5"));
                addEntry(zip, "Exam/2/tc2.txt", """
                        INPUT:
                        2
                        REMOVE_SPACES:
                        YES
                        CASE_SENSITIVE:
                        YES
                        """.getBytes(StandardCharsets.UTF_8));
            }

            assertThatThrownBy(() -> parser.parseFromTempFile(zipPath, ".zip"))
                    .isInstanceOf(InvalidZipStructureException.class)
                    .hasMessageContaining("Cau 2")
                    .hasMessageContaining("thieu 'OUTPUT:'");
        } finally {
            Files.deleteIfExists(zipPath);
        }
    }

    private Object invokeParseTcFile(ZipExamPaperParser parser, String content) throws Exception {
        Method method = ZipExamPaperParser.class.getDeclaredMethod(
                "parseTcFile", byte[].class, int.class, BigDecimal.class);
        method.setAccessible(true);

        try {
            return method.invoke(
                    parser,
                    content.getBytes(StandardCharsets.UTF_8),
                    1,
                    new BigDecimal("10.00"));
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            assertThat(cause).isNotNull();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            throw e;
        }
    }

    private byte[] docxBytes(String title, String scoreLine) throws IOException {
        try (XWPFDocument doc = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            doc.createParagraph().createRun().setText(title);
            doc.createParagraph().createRun().setText(scoreLine);
            doc.write(out);
            return out.toByteArray();
        }
    }

    private void addEntry(ZipOutputStream zip, String name, byte[] bytes) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(bytes);
        zip.closeEntry();
    }
}
