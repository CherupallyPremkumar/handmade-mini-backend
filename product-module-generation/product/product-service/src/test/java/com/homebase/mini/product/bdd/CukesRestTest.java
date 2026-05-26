package com.homebase.mini.product.bdd;

import org.springframework.test.context.ActiveProfiles;

import io.cucumber.junit.Cucumber;
import io.cucumber.junit.CucumberOptions;
import org.junit.runner.RunWith;

@RunWith(Cucumber.class)

@CucumberOptions(features = "src/test/resources/features",
    glue = {"classpath:com/homebase/mini/product/bdd",
    "classpath:org/chenile/cucumber/workflow",
    "classpath:org/chenile/cucumber/rest"},

        plugin = {"pretty"}
        )
@ActiveProfiles("unittest")
public class CukesRestTest {

}
