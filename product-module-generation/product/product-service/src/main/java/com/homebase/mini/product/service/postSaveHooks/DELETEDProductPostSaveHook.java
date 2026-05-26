package com.homebase.mini.product.service.postSaveHooks;

import com.homebase.mini.product.model.Product;
import org.chenile.stm.State;
import org.chenile.workflow.model.TransientMap;
import org.chenile.workflow.service.stmcmds.PostSaveHook;

/**
 Contains customized post Save Hook for the State ID.
*/
public class DELETEDProductPostSaveHook implements PostSaveHook<Product>{
	@Override
    public void execute(State startState, State endState, Product product, TransientMap map){
    }
}
