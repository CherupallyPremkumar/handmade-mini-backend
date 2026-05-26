package com.homebase.mini.product.configuration;

import org.chenile.stm.*;
import org.chenile.stm.action.STMTransitionAction;
import org.chenile.stm.impl.*;
import org.chenile.stm.spring.SpringBeanFactoryAdapter;
import org.chenile.workflow.param.MinimalPayload;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.chenile.utils.entity.service.EntityStore;
import org.chenile.workflow.service.impl.StateEntityServiceImpl;
import org.chenile.workflow.service.stmcmds.*;
import com.homebase.mini.product.model.Product;
import com.homebase.mini.product.service.ProductSkuService;
import com.homebase.mini.product.service.ProductStateEntityService;
import com.homebase.mini.product.service.cmds.*;
import com.homebase.mini.product.service.healthcheck.ProductHealthChecker;
import com.homebase.mini.product.service.store.ProductEntryAction;
import com.homebase.mini.product.service.store.ProductEntityStore;
import org.chenile.workflow.api.WorkflowRegistry;
import com.homebase.mini.product.service.postSaveHooks.*;

/**
 This is where you will instantiate all the required classes in Spring
*/
@Configuration
public class ProductConfiguration {
	private static final String FLOW_DEFINITION_FILE = "com/homebase/mini/product/product-states.xml";
	public static final String PREFIX_FOR_PROPERTIES = "Product";
    public static final String PREFIX_FOR_RESOLVER = "product";

    @Bean BeanFactoryAdapter productBeanFactoryAdapter() {
		return new SpringBeanFactoryAdapter();
	}
	
	@Bean STMFlowStoreImpl productFlowStore(
            @Qualifier("productBeanFactoryAdapter") BeanFactoryAdapter productBeanFactoryAdapter
            )throws Exception{
		STMFlowStoreImpl stmFlowStore = new STMFlowStoreImpl();
		stmFlowStore.setBeanFactory(productBeanFactoryAdapter);
		return stmFlowStore;
	}
	
	@Bean  STM<Product> productEntityStm(@Qualifier("productFlowStore") STMFlowStoreImpl stmFlowStore) throws Exception{
		STMImpl<Product> stm = new STMImpl<>();		
		stm.setStmFlowStore(stmFlowStore);
		return stm;
	}
	
	@Bean  STMActionsInfoProvider productActionsInfoProvider(@Qualifier("productFlowStore") STMFlowStoreImpl stmFlowStore) {
		STMActionsInfoProvider provider =  new STMActionsInfoProvider(stmFlowStore);
        WorkflowRegistry.addSTMActionsInfoProvider("product",provider);
        return provider;
	}
	
	@Bean EntityStore<Product> productEntityStore() {
		return new ProductEntityStore();
	}
	
	@Bean  ProductStateEntityService _productStateEntityService_(
			@Qualifier("productEntityStm") STM<Product> stm,
			@Qualifier("productActionsInfoProvider") STMActionsInfoProvider productInfoProvider,
			@Qualifier("productEntityStore") EntityStore<Product> entityStore){
		ProductStateEntityService service = new ProductStateEntityService(stm, productInfoProvider, entityStore);
		// Configure media validation thresholds.
		// Override via application properties: product.min-images / product.min-videos
		service.setMinImages(1);
		service.setMinVideos(0);   // 0 = video not mandatory
		return service;
	}
	
	// Now we start constructing the STM Components 


    @Bean  DefaultPostSaveHook<Product> productDefaultPostSaveHook(
    @Qualifier("productTransitionActionResolver") STMTransitionActionResolver stmTransitionActionResolver){
    DefaultPostSaveHook<Product> postSaveHook = new DefaultPostSaveHook<>(stmTransitionActionResolver);
    return postSaveHook;
    }

    /**
     * SKU generation service — used by ProductEntryAction during create flow.
     */
    @Bean ProductSkuService productSkuService() {
        return new ProductSkuService();
    }

    /**
     * Custom entry action that extends GenericEntryAction to inject SKU generation
     * BEFORE the entity is stored on create.
     * <p>
     * NOTE: Create flow goes through this entry action — NOT through
     * DefaultSTMTransitionAction. All other workflow events go through
     * DefaultSTMTransitionAction subclasses.
     * </p>
     */
    @Bean GenericEntryAction<Product> productEntryAction(
    @Qualifier("productEntityStore") EntityStore<Product> entityStore,
    @Qualifier("productActionsInfoProvider") STMActionsInfoProvider productInfoProvider,
    @Qualifier("productDefaultPostSaveHook") DefaultPostSaveHook<Product> postSaveHook,
    @Qualifier("productSkuService") ProductSkuService productSkuService,
    @Qualifier("productFlowStore") STMFlowStoreImpl stmFlowStore) {
        ProductEntryAction entryAction = new ProductEntryAction(
                entityStore, productInfoProvider, postSaveHook, productSkuService);
        stmFlowStore.setEntryAction(entryAction);
        return entryAction;
    }

    @Bean  DefaultAutomaticStateComputation<Product> productDefaultAutoState(
    @Qualifier("productTransitionActionResolver") STMTransitionActionResolver stmTransitionActionResolver,
    @Qualifier("productFlowStore") STMFlowStoreImpl stmFlowStore){
    DefaultAutomaticStateComputation<Product> autoState = new DefaultAutomaticStateComputation<>(stmTransitionActionResolver);
    stmFlowStore.setDefaultAutomaticStateComputation(autoState);
    return autoState;
    }

	@Bean GenericExitAction<Product> productExitAction(@Qualifier("productFlowStore") STMFlowStoreImpl stmFlowStore){
        GenericExitAction<Product> exitAction = new GenericExitAction<Product>();
        stmFlowStore.setExitAction(exitAction);
        return exitAction;
	}

	@Bean
	XmlFlowReader productFlowReader(@Qualifier("productFlowStore") STMFlowStoreImpl flowStore) throws Exception {
		XmlFlowReader flowReader = new XmlFlowReader(flowStore);
		flowReader.setFilename(FLOW_DEFINITION_FILE);
		return flowReader;
	}
	

	@Bean ProductHealthChecker productHealthChecker(){
    	return new ProductHealthChecker();
    }

    @Bean STMTransitionAction<Product> defaultproductSTMTransitionAction() {
        return new DefaultSTMTransitionAction<MinimalPayload>();
    }

    @Bean
    STMTransitionActionResolver productTransitionActionResolver(
    @Qualifier("defaultproductSTMTransitionAction") STMTransitionAction<Product> defaultSTMTransitionAction){
        return new STMTransitionActionResolver(PREFIX_FOR_RESOLVER,defaultSTMTransitionAction,true);
    }

    @Bean  StmBodyTypeSelector productBodyTypeSelector(
    @Qualifier("productActionsInfoProvider") STMActionsInfoProvider productInfoProvider,
    @Qualifier("productTransitionActionResolver") STMTransitionActionResolver stmTransitionActionResolver) {
        return new StmBodyTypeSelector(productInfoProvider,stmTransitionActionResolver);
    }

    @Bean  STMTransitionAction<Product> productBaseTransitionAction(
        @Qualifier("productTransitionActionResolver") STMTransitionActionResolver stmTransitionActionResolver,
        @Qualifier("productFlowStore") STMFlowStoreImpl stmFlowStore){
        BaseTransitionAction<Product> baseTransitionAction = new BaseTransitionAction<>(stmTransitionActionResolver);
        stmFlowStore.setDefaultTransitionAction(baseTransitionAction);
        return baseTransitionAction;
    }


    // Create the specific transition actions here. Make sure that these actions are inheriting from
    // AbstractSTMTransitionMachine (The sample classes provide an example of this). To automatically wire
    // them into the STM use the convention of "product" + eventId + "Action" for the method name. (product is the
    // prefix passed to the TransitionActionResolver above.)
    // This will ensure that these are detected automatically by the Workflow system.
    // The payload types will be detected as well so that there is no need to introduce an <event-information/>
    // segment in src/main/resources/com/mycompany/product/product-states.xml


    @Bean ApproveProductAction
            productApproveAction(){
        return new ApproveProductAction();
    }

    @Bean RejectProductAction
            productRejectAction(){
        return new RejectProductAction();
    }

    @Bean SubmitForReviewProductAction
            productSubmitForReviewAction(){
        return new SubmitForReviewProductAction();
    }

    @Bean ActivateProductAction
            productActivateAction(){
        return new ActivateProductAction();
    }

    @Bean DeleteProductAction
            productDeleteAction(){
        return new DeleteProductAction();
    }

    @Bean DeactivateProductAction
            productDeactivateAction(){
        return new DeactivateProductAction();
    }


    @Bean ConfigProviderImpl productConfigProvider() {
        return new ConfigProviderImpl();
    }

    @Bean ConfigBasedEnablementStrategy productConfigBasedEnablementStrategy(
        @Qualifier("productConfigProvider") ConfigProvider configProvider,
        @Qualifier("productFlowStore") STMFlowStoreImpl stmFlowStore) {
        ConfigBasedEnablementStrategy enablementStrategy = new ConfigBasedEnablementStrategy(configProvider,PREFIX_FOR_PROPERTIES);
        stmFlowStore.setEnablementStrategy(enablementStrategy);
        return enablementStrategy;
    }



    @Bean ACTIVEProductPostSaveHook
        productACTIVEPostSaveHook(){
            return new ACTIVEProductPostSaveHook();
    }

    @Bean DRAFTProductPostSaveHook
        productDRAFTPostSaveHook(){
            return new DRAFTProductPostSaveHook();
    }

    @Bean INACTIVEProductPostSaveHook
        productINACTIVEPostSaveHook(){
            return new INACTIVEProductPostSaveHook();
    }

    @Bean DELETEDProductPostSaveHook
        productDELETEDPostSaveHook(){
            return new DELETEDProductPostSaveHook();
    }

    @Bean UNDER_REVIEWProductPostSaveHook
        productUNDER_REVIEWPostSaveHook(){
            return new UNDER_REVIEWProductPostSaveHook();
    }

}
