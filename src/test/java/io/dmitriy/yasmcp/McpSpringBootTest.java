package io.dmitriy.yasmcp;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AliasFor;

/** {@link SpringBootTest} for the full application without a live STDIO transport (see {@link QuietMcpTransport}). */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@SpringBootTest
@Import(QuietMcpTransport.class)
public @interface McpSpringBootTest {

	@AliasFor(annotation = SpringBootTest.class, attribute = "properties")
	String[] properties() default {};

}
