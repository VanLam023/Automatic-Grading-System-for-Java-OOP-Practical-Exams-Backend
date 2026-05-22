package agsfjope.backend.application.bankservices.impl;

import agsfjope.backend.application.dtos.responses.wallet.BankOptionResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests cho BankLookupServiceImpl.
 * Phân loại: [N] Normal, [B] Boundary, [A] Abnormal.
 * Pattern: AAA (Arrange - Act - Assert).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BankLookupServiceImpl Tests")
class BankLookupServiceImplTest {

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private HttpClient mockHttpClient;

    @InjectMocks
    private BankLookupServiceImpl service;

    private HttpClient originalHttpClient;

    @BeforeEach
    void setUp() {
        // Save the original HttpClient to restore it later
        originalHttpClient = (HttpClient) ReflectionTestUtils.getField(BankLookupServiceImpl.class, "HTTP_CLIENT");
        // Inject the mocked HttpClient
        ReflectionTestUtils.setField(BankLookupServiceImpl.class, "HTTP_CLIENT", mockHttpClient);
    }

    @AfterEach
    void tearDown() {
        // Restore the original HttpClient
        ReflectionTestUtils.setField(BankLookupServiceImpl.class, "HTTP_CLIENT", originalHttpClient);
    }

    @Test
    @DisplayName("[N] getVietnamBanks - API trả về data hợp lệ -> Trả về danh sách đã sắp xếp")
    void getVietnamBanks_ValidResponse_ReturnsSortedList() throws Exception {
        // ── Arrange ──────────────────────────────────────────────────────────
        String jsonResponse = "{ \"data\": [ " +
                "{ \"code\": \"VCB\", \"name\": \"Vietcombank\", \"shortName\": \"VCB\", \"bin\": \"970436\", \"logo\": \"logo1.png\" }, " +
                "{ \"code\": \"TCB\", \"name\": \"Techcombank\", \"shortName\": \"TCB\", \"bin\": \"970415\", \"logo\": \"logo2.png\" } " +
                "] }";

        @SuppressWarnings("unchecked")
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(jsonResponse);

        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        ObjectMapper realMapper = new ObjectMapper();
        when(objectMapper.readTree(jsonResponse)).thenReturn(realMapper.readTree(jsonResponse));

        // ── Act ───────────────────────────────────────────────────────────────
        List<BankOptionResponse> result = service.getVietnamBanks();

        // ── Assert ────────────────────────────────────────────────────────────
        assertThat(result).hasSize(2);
        // Sorted by shortName (lowercase): "tcb" before "vcb"
        assertThat(result.get(0).getCode()).isEqualTo("TCB");
        assertThat(result.get(1).getCode()).isEqualTo("VCB");
    }

    @Test
    @DisplayName("[A] getVietnamBanks - API trả về lỗi HTTP 500 -> Trả về danh sách rỗng")
    void getVietnamBanks_HttpError_ReturnsEmptyList() throws Exception {
        // ── Arrange ──────────────────────────────────────────────────────────
        @SuppressWarnings("unchecked")
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(500);

        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        // ── Act ───────────────────────────────────────────────────────────────
        List<BankOptionResponse> result = service.getVietnamBanks();

        // ── Assert ────────────────────────────────────────────────────────────
        assertThat(result).isEmpty();
        verifyNoInteractions(objectMapper);
    }

    @Test
    @DisplayName("[A] getVietnamBanks - API ném ngoại lệ (Timeout/Network error) -> Trả về danh sách rỗng")
    void getVietnamBanks_NetworkException_ReturnsEmptyList() throws Exception {
        // ── Arrange ──────────────────────────────────────────────────────────
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new java.net.http.HttpTimeoutException("Timeout"));

        // ── Act ───────────────────────────────────────────────────────────────
        List<BankOptionResponse> result = service.getVietnamBanks();

        // ── Assert ────────────────────────────────────────────────────────────
        assertThat(result).isEmpty();
        verifyNoInteractions(objectMapper);
    }

    @Test
    @DisplayName("[B] getVietnamBanks - API trả về response không có mảng 'data' -> Trả về danh sách rỗng")
    void getVietnamBanks_NoDataArray_ReturnsEmptyList() throws Exception {
        // ── Arrange ──────────────────────────────────────────────────────────
        String jsonResponse = "{ \"message\": \"Not Found\" }";

        @SuppressWarnings("unchecked")
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(jsonResponse);

        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        ObjectMapper realMapper = new ObjectMapper();
        when(objectMapper.readTree(jsonResponse)).thenReturn(realMapper.readTree(jsonResponse));

        // ── Act ───────────────────────────────────────────────────────────────
        List<BankOptionResponse> result = service.getVietnamBanks();

        // ── Assert ────────────────────────────────────────────────────────────
        assertThat(result).isEmpty();
    }
    
    @Test
    @DisplayName("[B] getVietnamBanks - Data chứa phần tử null/thiếu field -> Bỏ qua phần tử lỗi, trả về phần tử hợp lệ")
    void getVietnamBanks_MissingFields_SkipsInvalidItems() throws Exception {
        // ── Arrange ──────────────────────────────────────────────────────────
        String jsonResponse = "{ \"data\": [ " +
                "{ \"code\": \"\", \"name\": \"\", \"shortName\": \"\" }, " +
                "{ \"code\": \"BIDV\", \"name\": \"BIDV\", \"shortName\": \"BIDV\", \"bin\": \"970418\", \"logo\": \"logo3.png\" } " +
                "] }";

        @SuppressWarnings("unchecked")
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(jsonResponse);

        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        ObjectMapper realMapper = new ObjectMapper();
        when(objectMapper.readTree(jsonResponse)).thenReturn(realMapper.readTree(jsonResponse));

        // ── Act ───────────────────────────────────────────────────────────────
        List<BankOptionResponse> result = service.getVietnamBanks();

        // ── Assert ────────────────────────────────────────────────────────────
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCode()).isEqualTo("BIDV");
    }
}
