package com.finshot.remittance.transfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.finshot.remittance.common.error.ValidationException;

class TransferQuoteTest {

    @ParameterizedTest(name = "sendAmount {0} -> fee {1}, totalDebit {2}, receiveAmount {3}")
    @CsvSource({
            "500000, 5000, 505000, 20600.00",  // example from the brief
            "10000,  3000, 13000,  412.00",    // smallest transfer: 1% is 100, minimum fee applies
            "300000, 3000, 303000, 12360.00",  // 1% is exactly the minimum
            "300049, 3000, 303049, 12362.02",  // 1% = 3000.49 -> rounds down
            "350050, 3501, 353551, 14422.06",  // 1% = 3500.50 -> HALF_UP rounds up
            "10013,  3000, 13013,  412.54",    // 412.5356 PHP -> rounds up
            "10001,  3000, 13001,  412.04",    // 412.0412 PHP -> rounds down
    })
    void calculatesFeeTotalAndReceiveAmount(long sendAmount, long fee, long totalDebit, String receiveAmount) {
        TransferQuote quote = TransferQuote.of(sendAmount);

        assertThat(quote.fee()).isEqualTo(fee);
        assertThat(quote.totalDebit()).isEqualTo(totalDebit);
        // toPlainString also checks the scale: "412.00", not "412" or "412.0".
        assertThat(quote.receiveAmount().toPlainString()).isEqualTo(receiveAmount);
    }

    @ParameterizedTest
    @CsvSource({"10000, 10000", "500000, 500000", "0500000, 500000"})
    void acceptsWholeNumbers(String raw, long expected) {
        assertThat(SendAmountParser.parse(raw)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"9999", "5000", "0", "-1", "500000.00", "12.5", "abc", "", "1e6", "+500000", " 500000",
            "1000000000000000"})
    void rejectsInvalidAmounts(String raw) {
        assertThatThrownBy(() -> SendAmountParser.parse(raw)).isInstanceOf(ValidationException.class);
    }
}
