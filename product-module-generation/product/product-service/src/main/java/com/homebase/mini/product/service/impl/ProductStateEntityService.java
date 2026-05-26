package com.homebase.mini.product.service;

import com.homebase.mini.product.model.Product;
import org.chenile.base.exception.ErrorNumException;
import org.chenile.stm.STM;
import org.chenile.stm.impl.STMActionsInfoProvider;
import org.chenile.utils.entity.service.EntityStore;
import org.chenile.workflow.service.impl.StateEntityServiceImpl;

/**
 * Product-specific {@link StateEntityServiceImpl} for the new Chenile module (v2).
 *
 * <p><strong>Chenile flow reference:</strong></p>
 * <pre>
 *  CREATE  → StateEntityServiceImpl.create()
 *               └─ processEntity(entity, null, null)
 *                     └─ stm.proceed(entity)           ← GenericEntryAction fires
 *                           └─ ProductEntryAction.execute()   ← SKU generated here
 *
 *  TRANSITION → StateEntityServiceImpl.processById(id, event, payload)
 *               └─ processEntity(entity, event, payload)
 *                     └─ stm.proceed(entity, event, payload)   ← specific STMAction fires
 *                           └─ e.g. ActivateProductAction, DeleteProductAction …
 * </pre>
 *
 * <p>By overriding {@link #processEntity} we can add <strong>pre-transition validation</strong>
 * that runs for every event, and override specific event handling as needed.</p>
 *
 * <p>Business logic ported from deprecated {@code com.pochampally.service.ProductService}:</p>
 * <ul>
 *   <li>Activation guard: cannot activate a product without minimum images / video</li>
 *   <li>Deletion guard: sets {@code isDeleted=true}, {@code isActive=false} before state change</li>
 *   <li>Deactivation: sets {@code isActive=false} before state change</li>
 *   <li>Activation: sets {@code isActive=true} before state change</li>
 * </ul>
 */
public class ProductStateEntityService extends StateEntityServiceImpl<Product> {

    // ── Injectable settings (wired by Spring from ProductConfiguration) ─────
    /** Minimum number of images required before a product can be activated. */
    private int minImages = 1;
    /** Minimum number of videos required before a product can be activated (0 = not required). */
    private int minVideos = 0;

    public ProductStateEntityService(STM<Product> stm,
                                     STMActionsInfoProvider stmActionsInfoProvider,
                                     EntityStore<Product> entityStore) {
        super(stm, stmActionsInfoProvider, entityStore);
    }

    public void setMinImages(int minImages) { this.minImages = minImages; }
    public void setMinVideos(int minVideos) { this.minVideos = minVideos; }

    // ════════════════════════════════════════════════════════════════
    //  OVERRIDE — runs for every CREATE and every TRANSITION
    // ════════════════════════════════════════════════════════════════

    /**
     * Central hook that intercepts every create and every transition.
     *
     * <ul>
     *   <li>{@code event == null}  → create flow   → calls {@code stm.proceed(entity)}</li>
     *   <li>{@code event != null}  → transition     → applies per-event business rules,
     *                                then calls {@code stm.proceed(entity, event, payload)}</li>
     * </ul>
     *
     * @param entity  the product being created or transitioned
     * @param event   the workflow event ID (null on create)
     * @param payload the event-specific payload
     * @return the mutated entity with its new state
     */
    @Override
    protected Product processEntity(Product entity, String event, Object payload) {

        if (event != null) {
            // ── Per-event business logic BEFORE the STM fires ──────────
            switch (event) {

                case "activate":
                    // Guard: cannot activate without required media
                    validateMediaForActivation(entity);
                    // Set the flag so the entity reflects active=true after save
                    // why cant we use processById rather then chnaging here handler there action processById()
                    break;

                case "deactivate":
                    // Mark inactive before state transition
                    entity.setIsActive(false);
                    break;

                case "delete":
                    // Soft-delete: mark both flags before state transition
                    entity.setIsDeleted(true);
                    entity.setIsActive(false);
                    break;

                case "update":
                    // No pre-processing needed here — fields are already merged
                    // by the Chenile HTTP layer before processById() is called.
                    break;

                case "submitForReview":
                    // Validate description fields are not empty before review
                    validateForReview(entity);
                    break;

                default:
                    // approve, reject, custom events — no extra pre-processing
                    break;
            }
        }

        // ── Delegate to parent → triggers stm.proceed() ────────────
        return super.processEntity(entity, event, payload);
    }

    // ════════════════════════════════════════════════════════════════
    //  BUSINESS RULE HELPERS  (ported from old ProductService)
    // ════════════════════════════════════════════════════════════════

    /**
     * Validates that the product has enough images and video before activation.
     * Mirrors the media guard from the deprecated {@code ProductController.toggleActive()}.
     */
    private void validateMediaForActivation(Product product) {
        int imgCount = product.getImages() != null ? product.getImages().size() : 0;
        if (imgCount < minImages) {
            throw new ErrorNumException(422, 7001,
                    "Cannot activate: minimum " + minImages + " image(s) required. " +
                    "Product currently has " + imgCount + ".");
        }
        if (minVideos > 0 &&
            (product.getVideoUrl() == null || product.getVideoUrl().isBlank())) {
            throw new ErrorNumException(422, 7002,
                    "Cannot activate: minimum " + minVideos + " video required. " +
                    "Please upload a video first.");
        }
    }

    /**
     * Validates that the product has a name and description before being submitted for review.
     */
    private void validateForReview(Product product) {
        if (product.getName() == null || product.getName().isBlank()) {
            throw new ErrorNumException(422, 7003,
                    "Product name is required before submitting for review.");
        }
        if (product.getDescription() == null || product.getDescription().isBlank()) {
            throw new ErrorNumException(422, 7004,
                    "Product description is required before submitting for review.");
        }
    }
}
class Main{

    public static void main(String[] args){

        int[] arr = {1,9,3};

        int i = 0;

        arr[i++] += arr[++i] += ++i - i++;

        System.out.println(arr[1] + " " + i);

    }
}