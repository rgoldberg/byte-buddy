package net.bytebuddy.dynamic.scaffold.inline;

import net.bytebuddy.instrumentation.method.MethodDescription;
import net.bytebuddy.utility.MockitoRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.mockito.Mock;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class MethodFlatteningResolverNoOpTest {

    @Rule
    public TestRule mockitoRule = new MockitoRule(this);

    @Mock
    private MethodDescription methodDescription;

    @Test
    public void testResolutionContainsCorrectData() throws Exception {
        MethodFlatteningResolver.Resolution resolution = MethodFlatteningResolver.NoOp.INSTANCE.resolve(methodDescription);
        assertThat(resolution.isRebased(), is(false));
        assertThat(resolution.getResolvedMethod(), is(methodDescription));
    }

    @Test(expected = IllegalStateException.class)
    public void testAdditionalArgumentsAreIllegal() throws Exception {
        MethodFlatteningResolver.NoOp.INSTANCE.resolve(methodDescription).getAdditionalArguments();
    }
}
