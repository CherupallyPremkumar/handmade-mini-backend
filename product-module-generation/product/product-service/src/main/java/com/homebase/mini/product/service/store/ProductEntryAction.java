package com.homebase.mini.product.service.store;

import com.homebase.mini.product.model.Product;
import com.homebase.mini.product.service.ProductSkuService;
import org.chenile.stm.State;
import org.chenile.stm.impl.STMActionsInfoProvider;
import org.chenile.utils.entity.service.EntityStore;
import org.chenile.workflow.service.stmcmds.GenericEntryAction;
import org.chenile.workflow.service.stmcmds.PostSaveHook;

/**
 * Custom entry action for Product creation flow.
 * <p>
 * In Chenile, <strong>create</strong> goes through {@link GenericEntryAction},
 * NOT through {@link com.homebase.mini.product.service.cmds.DefaultSTMTransitionAction}.
 * <br>
 * This subclass hooks in before {@code entityStore.store()} is called to:
 * <ol>
 *   <li>Auto-generate a SKU if the product doesn't have one yet</li>
 * </ol>
 * </p>
 * <p>
 * All other workflow transitions (update, approve, activate …) go through
 * {@link com.homebase.mini.product.service.cmds.DefaultSTMTransitionAction} and do NOT
 * reach this class.
 * </p>
 */
public class ProductEntryAction extends GenericEntryAction<Product> {

    private final ProductSkuService productSkuService;

    public ProductEntryAction(EntityStore<Product> entityStore,
                              STMActionsInfoProvider stmActionsInfoProvider,
                              PostSaveHook<Product> postSaveHook,
                              ProductSkuService productSkuService) {
        super(entityStore, stmActionsInfoProvider, postSaveHook);
        this.productSkuService = productSkuService;
    }

    @Override
    public void execute(State startState, State endState, Product product) throws Exception {
        // ── Business logic BEFORE the entity is stored ─────────────
        // Auto-generate SKU for new products (only on create — startState is null)
        if (startState == null) {
            productSkuService.ensureSku(product);
        }

        // ── Delegate to Chenile's GenericEntryAction ────────────────
        // This sets workflow timestamps (stateEntryTime, sla…) and calls store()
        super.execute(startState, endState, product);
    }
}
