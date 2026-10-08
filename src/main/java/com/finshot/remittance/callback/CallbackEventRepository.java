package com.finshot.remittance.callback;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CallbackEventRepository extends JpaRepository<CallbackEvent, String> {
}
