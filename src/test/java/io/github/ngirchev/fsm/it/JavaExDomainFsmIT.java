package io.github.ngirchev.fsm.it;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.github.ngirchev.fsm.FsmFactory;
import io.github.ngirchev.fsm.impl.extended.ExDomainFsm;
import io.github.ngirchev.fsm.it.document.Document;
import io.github.ngirchev.fsm.it.document.DocumentState;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static io.github.ngirchev.fsm.it.document.DocumentState.*;

class JavaExDomainFsmIT {

    private static final Logger log = LoggerFactory.getLogger(JavaExDomainFsmIT.class);

    @Test
    void successfulPath() {
        Document document = new Document("1", NEW, true, null);

        ExDomainFsm<Document, DocumentState, String> fsm =
                FsmFactory.INSTANCE.<DocumentState, String>statesWithEvents()
                        .from(NEW).to(READY_FOR_SIGN).onEvent("TO_READY").end()

                        .from(READY_FOR_SIGN).toMultiple()
                        .to(SIGNED).onEvent("USER_SIGN").end()
                        .to(CANCELED).onEvent("FAILED_EVENT").end()
                        .endMultiple()

                        .from(SIGNED).onEvent("TO_END").toMultiple()
                        .to(AUTO_SENT).condition(ctx -> ((Document) ctx).getSignRequired())
                        .action(ctx -> System.out.println("AUTO_SENT"))
                        .end()
                        .to(DONE).condition(ctx -> !((Document) ctx).getSignRequired()).end()
                        .to(CANCELED).end()
                        .endMultiple()

                        .from(AUTO_SENT).onEvent("TO_END").to(DONE).end()
                        .build()
                        .createDomainFsm();

        try {
            fsm.handle(document, "FAILED_EVENT");
        } catch (Exception ex) {
            log.error("e: ", ex);
        }

        System.out.println("State still the same - NEW = " + document.getState());

        fsm.handle(document, "TO_READY");
        System.out.println("READY_FOR_SIGN = " + document.getState());

        fsm.handle(document, "USER_SIGN");
        System.out.println("SIGNED = " + document.getState());

        fsm.handle(document, "TO_END");
        System.out.println("AUTO_SENT = " + document.getState());

        fsm.handle(document, "TO_END");
        System.out.println("Terminal state is DONE = " + document.getState());
        assertEquals(DONE, document.getState());
    }
}