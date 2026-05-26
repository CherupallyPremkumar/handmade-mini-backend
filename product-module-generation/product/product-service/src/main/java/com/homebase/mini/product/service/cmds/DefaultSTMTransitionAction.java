package com.homebase.mini.product.service.cmds;

import org.chenile.stm.STMInternalTransitionInvoker;
import org.chenile.stm.State;
import org.chenile.stm.model.Transition;
import org.chenile.workflow.param.MinimalPayload;
import org.chenile.workflow.service.stmcmds.AbstractSTMTransitionAction;
import com.homebase.mini.product.model.Product;

/**
 * Default transition action — runs for every STM <strong>transition</strong> event
 * (update, approve, activate, deactivate, delete, submitForReview…).
 * <p>
 * <strong>Important:</strong> This class is NOT called for product <em>creation</em>.
 * Create goes through {@link com.homebase.mini.product.service.store.ProductEntryAction}
 * which extends {@code GenericEntryAction}.
 * </p>
 * <p>
 * Add cross-cutting transition logic here (e.g. audit trail, validation that applies
 * to all state changes). Specific per-event logic lives in the individual action classes
 * (e.g. {@link ActivateProductAction}, {@link DeleteProductAction}).
 * </p>
 */
public class DefaultSTMTransitionAction<PayloadType extends MinimalPayload>
    extends AbstractSTMTransitionAction<Product, PayloadType> {

    @Override
    public void transitionTo(Product product, PayloadType payload,
                 State startState, String eventId, State endState,
                 STMInternalTransitionInvoker<?> stm, Transition transition) {
        // Add generic pre-save logic for ALL transitions here.
        // e.g. audit logging, timestamp updates, validation checks.
        // Per-event logic belongs in the specific action class (e.g. ActivateProductAction).
    }
}