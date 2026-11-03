package cz.mibcon.zazitky.billing;

import java.time.YearMonth;
import java.util.UUID;

import cz.mibcon.zazitky.billing.BillingDtos.MarkInvoicedRequest;
import cz.mibcon.zazitky.billing.BillingDtos.MonthBilling;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Invoicing of a castle by months; only for the admin. Months as YYYY-MM. */
@RestController
@RequestMapping("/api/v1/admin/partners/{partnerId}/billing/{month}")
public class BillingController {

    private final BillingService billing;

    public BillingController(BillingService billing) {
        this.billing = billing;
    }

    @GetMapping
    public MonthBilling month(@PathVariable UUID partnerId, @PathVariable YearMonth month) {
        return billing.month(partnerId, month);
    }

    @PutMapping("/invoice")
    public MonthBilling markInvoiced(@PathVariable UUID partnerId, @PathVariable YearMonth month,
            @Valid @RequestBody MarkInvoicedRequest request) {
        return billing.markInvoiced(partnerId, month, request.invoicedOn());
    }

    @DeleteMapping("/invoice")
    public MonthBilling unmarkInvoiced(@PathVariable UUID partnerId, @PathVariable YearMonth month) {
        return billing.unmarkInvoiced(partnerId, month);
    }
}
