package agsfjope.backend.application.bankservices;

import agsfjope.backend.application.dtos.responses.wallet.BankOptionResponse;

import java.util.List;

/**
 * Service lấy danh sách ngân hàng Việt Nam phục vụ form rút tiền.
 */
public interface BankLookupService {
    List<BankOptionResponse> getVietnamBanks();
}
