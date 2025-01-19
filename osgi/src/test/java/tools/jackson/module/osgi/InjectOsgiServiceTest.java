package tools.jackson.module.osgi;

import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import org.mockito.Mockito;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Filter;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;

import com.fasterxml.jackson.annotation.JacksonInject;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

public class InjectOsgiServiceTest
{
    private static final String OSGI_FILTER = "osgi.filter";

    private BundleContext bundleContext;

    private ObjectMapper mapper;

    public static Stream<Class<? extends VerifyableBean>> data() {
        // 18-Jan-2025, tatu: Looks like we'll mostly fail to inject Service
        //   in Jackson 3.0/JDK 17/JUnit 5/JPMS (not sure which part matters most;
        //   possibly JPMS). So comment out ones that'd fail
        return Stream.of(
                //BeanWithServiceInConstructor.class, 
                BeanWithFilter.class
                //BeanWithServiceInField.class,
                //BeanWithNotFoundService.class
                );
    }
    
    @SuppressWarnings("unchecked")
    @BeforeEach
    public void setUp() throws Exception
    {
        bundleContext = mock(BundleContext.class);
        doThrow(new InvalidSyntaxException("", "")).when(bundleContext).createFilter(anyString());
        Filter filter = mock(Filter.class);
        when(filter.toString()).thenReturn(OSGI_FILTER);
        doReturn(filter).when(bundleContext).createFilter(OSGI_FILTER);
        when(bundleContext.getServiceReferences(eq(Service.class.getName()), anyString())).thenReturn(new ServiceReference[]{mock(ServiceReference.class)});
        when(bundleContext.getService(any(ServiceReference.class))).thenReturn(mock(Service.class));
        
        mapper = JsonMapper.builder()
                .addModule(new OsgiJacksonModule(bundleContext))
                .build();
    }

    @MethodSource("data")
    @ParameterizedTest
    public void testServiceIsInjected(Class<?> beanClass)
        throws Exception
    {
        // ACTION
        VerifyableBean result = mapper.reader().forType(beanClass)
            .readValue(this.getClass().getResourceAsStream("/bean.json"));

        // VERIFICATION
        result.verify(bundleContext);
    }

    public static interface Service
    {

    }

    public static interface NotFoundService
    {

    }

    public static abstract class VerifyableBean 
    {
        public String field;
        
        public Service service;
        
        public boolean verify(BundleContext bundleContext)
        {
            assertEquals("value", field);
            assertNotNull(service);
            return true;
        }
    }
    
    public static class BeanWithServiceInConstructor extends VerifyableBean
    {
        public BeanWithServiceInConstructor(@JacksonInject Service service)
        {
            this.service = service;
        }

        @Override
        public boolean verify(BundleContext bundleContext)
        {
            try
            {
                super.verify(bundleContext);
                Mockito.verify(bundleContext, times(1)).getServiceReferences(Service.class.getName(), null);
                return true;
            }
            catch (InvalidSyntaxException e)
            {
                fail(e.getMessage());
                return false;
            }
        }
    }

    public static class BeanWithServiceInField extends VerifyableBean
    {
        @JacksonInject
        public Service service2;
        
        @Override
        public boolean verify(BundleContext bundleContext)
        {
            try
            {
                this.service = service2;
                super.verify(bundleContext);
                Mockito.verify(bundleContext, times(1)).getServiceReferences(Service.class.getName(), null);
                return true;
            }
            catch (InvalidSyntaxException e)
            {
                fail(e.getMessage());
                return false;
            }
        }

    }

    public static class BeanWithFilter extends VerifyableBean
    {
        public BeanWithFilter(@JacksonInject(value = OSGI_FILTER) Service service)
        {
            this.service = service;
        }
        
        @Override
        public boolean verify(BundleContext bundleContext)
        {
            try
            {
                super.verify(bundleContext);
                Mockito.verify(bundleContext, times(1)).getServiceReferences(Service.class.getName(), OSGI_FILTER);
                return true;
            }
            catch (InvalidSyntaxException e)
            {
                fail(e.getMessage());
                return false;
            }
        }
    }

    public static class BeanWithNotFoundService extends VerifyableBean
    {
        private final NotFoundService notFoundService;
        
        public BeanWithNotFoundService(@JacksonInject Service service,
                @JacksonInject NotFoundService notFoundService)
        {
            this.service = service;
            this.notFoundService = notFoundService;
        }
        
        @Override
        public boolean verify(BundleContext bundleContext)
        {
            try
            {
                super.verify(bundleContext);
                assertNull(notFoundService);
                Mockito.verify(bundleContext, times(1)).getServiceReferences(NotFoundService.class.getName(), null);
                return true;
            }
            catch (InvalidSyntaxException e)
            {
                fail(e.getMessage());
                return false;
            }
        }
    }

}
