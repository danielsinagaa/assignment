package com.finshot.remittance.partner;

/** One call to the Philippine partner. A real client may also throw on timeouts; the caller retries both cases. */
public interface PartnerClient {

    PartnerOutcome submit(String partnerRef);
}
