package net.bytebuddy.description.field;

import net.bytebuddy.description.ByteCodeElement;
import net.bytebuddy.matcher.AbstractFilterableListTest;
import org.junit.Test;

import java.util.Collections;

import static net.bytebuddy.matcher.ElementMatchers.none;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public abstract class AbstractFieldListTest<U, V extends FieldDescription> extends AbstractFilterableListTest<V, FieldList<V>, U> {

    @Test
    @SuppressWarnings("unchecked")
    public void testTokenWithoutMatcher() throws Exception {
        assertThat(asList(Collections.singletonList(getFirst())).asTokenList(), is(new ByteCodeElement.Token
                .TokenList<FieldDescription.Token>(Collections.singletonList(asElement(getFirst()).asToken()))));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testTokenWithMatcher() throws Exception {
        assertThat(asList(Collections.singletonList(getFirst())).asTokenList(none()), is(new ByteCodeElement.Token
                .TokenList<FieldDescription.Token>(Collections.singletonList(asElement(getFirst()).asToken(none())))));
    }

    @Test
    public void testDeclared() throws Exception {
        assertThat(asList(Collections.singletonList(getFirst())).asDefined(), is(Collections.singletonList(asElement(getFirst()).asDefined())));
    }

    protected static class Foo {

        Void foo;

        Void bar;
    }
}
