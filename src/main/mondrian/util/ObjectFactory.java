/*
// $Id$
// This software is subject to the terms of the Common Public License
// Agreement, available at the following URL:
// http://www.opensource.org/licenses/cpl.html.
// Copyright (C) 2007-2007 Julian Hyde and others
// All Rights Reserved.
// You must accept the terms of that agreement to use this software.
*/
package mondrian.util;

import java.lang.reflect.Constructor;
import java.util.Properties;

/**
 * Concrete derived classes of the generic <code>ObjectFactory</code> class
 * are used to produce an implementation of an interface. In general, a
 * factory should produce a default implementation for general application
 * use as well as particular implementations used during testing.
 * During testing of application code and during normal execution,
 * the application code uses one of the <code>ObjectFactory</code>'s
 * methods for producing implementation instances - the same method is
 * used both for test and non-test modes. There are two ways of
 * modifying the implementation returned to the application code.
 * The first is for the application to use Properties.
 * The <code>ObjectFactory</code> implementation looks for a given
 * property (by default the name of the property is the class name
 * of the interfaceClass object) and if found uses it as the classname
 * to create.
 * A second approach is to use a ThreadLocal; if the ThreadLocal
 * is non-empty then use it as the classname.
 * <p>
 * When to use a Factory?
 * <p>
 * Everyone has an opinion. For me, there are two criteria: enabling
 * unit testing and providing end-user/developer-customizer overriding.
 * <p>
 * If a method has side-effects, either its result depends upon
 * a side-effect or calling it causes a side-effect, then the Object
 * hosting the method is a candidate for having a factory. Why?
 * Well, consider the case where a method returns the value of
 * a System property and the System property is determined only once
 * and set to a static final variable:
 * <pre>
 *      class OneValue {
 *          private static final boolean propValue;
 *          static {
 *              propValue = Boolean.getBoolean("com.app.info.value");
 *          }
 *          .....
 *          public boolean hasInfo() {
 *              return propValue;
 *          }
 *      }
 * </pre>
 * <p>
 * In this case, only one value is ever returned. If you have a
 * module, a client of the above code, that uses the value returned
 * by a call to the
 * <code>hasInfo()</code> method, how do you write a unit test of
 * your module that tests both possible return values?
 * You can not, its value is based upon a side-effect, an external
 * value that can not be controled by the unit test.
 * If the <code>OneValue</code> class was an interface and there was a factory,
 * then the unit test could arrange that its own version of the
 * <code>OneValue</code>
 * interface was returned and in one test arrange that <code>true</code>
 * was returned and in a second test, arrange that <code>false</code>
 * was returned.
 * <p>
 * The above is a trivial example of code that disallows clients of the
 * code from being properly tested.
 * <p>
 * Another example might be a module that directly initializes a JMS
 * queue and receives JMS message
 * from the JMS queue. This code can not be tested without having a live
 * JMS queue. On the other hand, if one defines an interface allowing
 * one to wrap access to the JMS queue and accesses the implementation
 * via a factory, then unit tests can be create that use a mock
 * JMS queue.
 * <p>
 * With regards to providing end-user/developer-customizer overriding,
 * its generally good to have a flexible application framework.
 * Experimental or just different implementations can be developed and
 * tested without having to touch a lot of the application code itself.
 * <p>
 * There is, of course, a trade-off between the use of a factory
 * and the size or simplicity of the object being created.
 * <p>
 * What are the requirements for a template ObjectFactory?
 * <p>
 * First, every implementation must support the writing of unit tests.
 * What this means it that test cases can override what the factory
 * produces. The test cases can all use the same produced Object or
 * each can request an Object targeted to its particular test. All this
 * without changing the <code>default</code> behavior of the factory.
 * <p>
 * Next, it should be possible to create a factory from the template that
 * is intended to deliver the same Object each time it is called, a
 * different, new Object each time it is called, or, based on the
 * calling environment (parameters, properties, <code>ThreadLocal</code>,
 * etc.) one of a set of Objects. These are possible <code>default</code>
 * behaviors, but, again, they can be overridden for test purposes.
 * <p>
 * While a factory has a <code>default</code> behavior in an
 * application, it must be possible for every factory's behavior
 * in that application to be globally overridden. What that means is
 * if the application designer has dictated a <code>default</code>, the
 * application user should be able to change the default. An example of
 * this is overriding what Object is returned based upon a
 * <code>System</code> property value.
 * <p>
 * Lastly, every factory is a singleton - if an interface with 
 * an implementation whose creation is mediated by a factory, then
 * there is a single factory that does that creating.
 * This does not mean that such a factory always return the same value, 
 * rather that there is only one instance of the factory itself.
 * <p>
 * The following is an example class that generates a factory
 * singleton. In this case, the factory extends the
 * <code>ObjectFactory</code>
 * rather than the <code>ObjectFactory.Singleton</code>:
 * <pre>
 *
 *      public final class FooFactory extends ObjectFactory<Foo> {
 *          // The single instance of the factory
 *          private static final FooFactory factory;
 *          static {
 *              factory = new FooFactory();
 *          }
 *          public static FooFactory instance() {
 *              return factory;
 *          }
 *          ..........
 *          private FooFactory() {
 *              super(Foo.class);
 *          }
 *          ..........
 *      }
 *
 * </pre>
 * <p>
 * There are multiple ways of creating derived classes that have support
 * for unit testing. A very simple way is to use <code>ThreadLocal</code>s.
 *
 * <pre>
 *
 *          private static final ThreadLocal ClassName = new ThreadLocal();
 *          private static String getThreadLocalClassName() {
 *              return (String) ClassName.get();
 *          }
 *          public static void setThreadLocalClassName(String className) {
 *              ClassName.set(className);
 *          }
 *          public static void clearThreadLocalClassName() {
 *              ClassName.set(null);
 *          }
 *          ..........
 *          protected String getClassName() {
 *              return getThreadLocalClassName();
 *          }
 *
 * </pre>
 * <p>
 * Here, the unit test will call the <code>setThreadLocalClassName</code>
 * method setting it with the class name of a specialized implemetation of
 * the template interface. In the <code>finally</code> clause of the
 * unit test, it is very important that there be a call to the
 * <code>clearThreadLocalClassName</code> method so that other
 * tests, etc. do not get an instance of the test-specific specialized
 * implementation.
 * <p>
 * The following is an example unit test that uses the factory's 
 * <code>ThreadLocal</code> to override the implementation that is returned.
 * 
 * <pre>
 *      interface Boo {
 *          boolean getValue();
 *          .......
 *      }
 * 
 *      class MyTest {
 *          private static boolean testValue;
 *          static class BooTest implements Boo {
 *              public boolean getValue() {
 *                  return MyTest.testValue;
 *              }
 *          }
 *          public void test() {
 *              BooFactory bf = BooFactory.instance();
 *              try {
 *                  BooFactory.setThreadLocalClassName("MyTest.BooTest");
 * 
 *                  MyTest.testValue = true;
 *                  Boo boo1 = bf.getObject();
 *                  assertTrue("Value not true", boo1.getValue());
 * 
 *                  MyTest.testValue = false;
 *                  Boo boo2 = bf.getObject();
 *                  assertTrue("Value is true", ! boo2.getValue());
 *              } finally {
 *                  BooFactory.clearThreadLocalClassName();
 *              }
 *          }
 *      }
 *
 * </pre>
 * <p>
 * While this is a very simple example, it shows how using such factories
 * can aid in creating testable code.
 *
 * @author <a>Richard M. Emberson</a>
 * @since Feb 01 2007
 * @version $Id$
 */
public abstract class ObjectFactory<V> {

    private static final Class[] EMPTY_CLASS_ARRAY = new Class[0];
    private static final Object[] EMPTY_OBJECT_ARRAY = new Object[0];

    /**
     * The type of the object to be generated.
     */
    private final Class<V> interfaceClass;

    /**
     * Creates a new factory object. The <code>interfaceClass</code> parameter
     * is used to cast the object generated to type right type.
     *
     * @param interfaceClass the class object for the interface implemented
     * by the objects returned by this factory
     *
     */
    protected ObjectFactory(final Class<V> interfaceClass) {
        this.interfaceClass = interfaceClass;
    }

    /**
     * Constructs an object where the System Properties can be used
     * to look up a class name.
     * The constructor for the object takes no parameters.
     *
     * @return the newly created object
     * @throws CreationException if unable to create the object
     */
    public final V getObject() throws CreationException {
        return getObject(System.getProperties());
    }

    /**
     * Constructs an object where the <code>Properties</code> parameter can
     * be used to look up a class name.
     * The constructor for the object takes no parameters.
     *
     * @param props the property definitions to use to determine the
     * implementation class
     *
     * @return the newly created object
     * @throws CreationException if unable to create the object
     */
    public final V getObject(final Properties props) throws CreationException {
        return getObject(props, EMPTY_CLASS_ARRAY, EMPTY_OBJECT_ARRAY);
    }

    /**
     * Constructs an object where the <code>parameterTypes</code> and
     * <code>parameterValues</code> are constructor parameters and
     * System Properties are used to look up a class name.
     *
     * @param parameterTypes  the class parameters that define the signature
     * of the constructor to use
     * @param parameterValues  the values to use to construct the current
     * instance of the object
     * @return the newly created object
     * @throws CreationException if unable to create the object
     */
    public final V getObject(final Class[] parameterTypes,
                             final Object[] parameterValues)
            throws CreationException {
        return getObject(System.getProperties(),
                         parameterTypes,
                         parameterValues);
    }

    /**
     * Constructs an object where the <code>parameterTypes</code> and
     * <code>parameterValues</code> are constructor parameters and
     * Properties parameter is used to look up a class name.
     * <p>
     * This returns a new instance of the Object each time its
     * called (assuming that if the method <code>getDefault</code>,
     * which derived classes implement), if called, creates a new
     * object each time.
     *
     * @param props the property definitions to use to determine the
     * @param parameterTypes  the class parameters that define the signature
     * of the constructor to use
     * @param parameterValues  the values to use to construct the current
     * instance of the object
     * @return the newly created object
     * @throws CreationException if unable to create the object
     */
    public V getObject(final Properties props,
                             final Class[] parameterTypes,
                             final Object[] parameterValues)
            throws CreationException {

        // Unit test override
        final String className = getClassName();
        if (className != null) {
            return getObject(className, parameterTypes, parameterValues);
        }

        final String propClassName = getClassName(props);
        return (propClassName != null)
                // User overriding application default
            ? getObject(propClassName, parameterTypes, parameterValues)
                // Get application default
            : getDefault(parameterTypes, parameterValues);
    }

    /**
     * Creates an instance with the given <code>className</code>,
     * <code>parameterTypes</code> and <code>parameterValues</code> or
     * throw a <code>CreationException</code>. This uses reflection
     * to create the instance typing the generated Object based upon
     * the <code>interfaceClass</code> factory instance object.
     *
     * @param className the class name used to create Object instance
     * @param parameterTypes  the class parameters that define the signature
     * of the constructor to use
     * @param parameterValues  the values to use to construct the current
     * instance of the object
     * @return the newly created object
     * @throws CreationException if unable to create the object
     */
    protected V getObject(final String className,
                          final Class[] parameterTypes,
                          final Object[] parameterValues)
            throws CreationException {
        try {
            // As reference google the source for:
            //   org.apache.cxf.BusFactoryHelper.java
            final ClassLoader loader =
                Thread.currentThread().getContextClassLoader();
            final Class<?> genericClass =
                Class.forName(className, true, loader);
            final Class<? extends V> specificClass =
                asSubclass(this.interfaceClass, genericClass);
            final Constructor<? extends V> constructor =
                specificClass.getConstructor(parameterTypes);

            return constructor.newInstance(parameterValues);

        } catch (Exception exc) {
            throw new CreationException("Error creating object of type \"" +
                                        getClass().getName() + "\"" ,
                                        exc);
        }
    }

    /**
     * This is a back port of a 1.5 version Class method.
     *
     * @param clazz the base class which the genericClass will be case
     * @param genericClass the class to be cast to the base clazz
     * @return this <tt>Class</tt> object, cast to represent a subclass of
     * the specified class object.
     * @throws ClassCastException if this <tt>Class</tt> object does
     * not represent a subclass of the specified class (here "subclass" 
     * includes the class itself).
     */
    private static <V> Class<? extends V> asSubclass(final Class<V> clazz,
                                              final Class<?> genericClass) {
        if (clazz.isAssignableFrom(genericClass)) {
            return (Class<? extends V>) genericClass;
        } else {
            throw new ClassCastException(genericClass.toString());
        }
    }

    /**
     * Returns the name of a class to use to create an object.
     * The default implementation returns null but derived
     * classes can return a class name.
     * <p>
     * This method is the primary mechanism for supporting Unit testing.
     * A derived class can have, as an example, this method return
     * the value of a <code>ThreadLocal</code>. For testing it
     * return a class name while for normal use it returns <code>null</code>.
     *
     * @return <code>null</code> or a class name
     */
    protected String getClassName() {
        return null;
    }

    /**
     * Returns the name of a class to use to create an object.
     * By default the name returned is the value of a property
     * with key equal to the class name of the <code>interfaceClass</code>.
     * This method is allowed to return null.
     *
     * @return <code>null</code> or a class name
     */
    protected String getClassName(final Properties props) {
        return (props == null)
            ? null : props.getProperty(this.interfaceClass.getName());
    }

    /**
     * For most uses (other than testing) this is the method that derived
     * classes implement that return the desired object.
     *
     * @param parameterTypes  the class parameters that define the signature
     * of the constructor to use
     * @param parameterValues  the values to use to construct the current
     * instance of the object
     * @return the newly created object
     * @throws CreationException if unable to create the object
     */
    protected abstract V getDefault(Class[] parameterTypes,
                                    Object[] parameterValues)
        throws CreationException;

    /**
     * Factory method which creates an exception to be thrown
     * if an object can not be created.
     *
     * @return
     */
    // REVIEW: jhyde, 2007/2/4: CreationException is superfluous, since it's
    // unlikely that anyone will want to handle it. This code should wrap the
    // error using Util.newError, just like elsewhere in mondrian.
    protected CreationException defaultCreationException() {
        return new CreationException("Error creating object of type \"" +
                                            getClass().getName() + "\"");
    }

    /**
     * Implementation of ObjectFactory
     * that returns only a single instance of the Object.
     */
    public abstract static class Singleton<T> extends ObjectFactory<T> {

        /** 
         * The single instance of the object created by the factory. 
         */
        protected T singleInstance;

        /**
         * Creates a new singleton factory object. The
         * <code>interfaceClass</code> parameter
         * is used to cast the object generated to type right type.
         *
         * @param interfaceClass the class object for the interface implemented
         * by the objects returned by this factory
         */
        protected Singleton(final Class<T> interfaceClass) {
            super(interfaceClass);
        }

        /**
         * Returns the singleton Object.
         * The first time this is called, an object is created where
         * the <code>parameterTypes</code> and
         * <code>parameterValues</code> are constructor parameters and
         * Properties parameter is used to look up a class name.
         * <p>
         * This returns a same instance of the Object each time its
         * called except if the <code>getClassName</code> method
         * returns a non-null class name which should only
         * happen as needed for unit testing.
         *
         * @param props the property definitions to use to determine the
         * @param parameterTypes  the class parameters that define the signature
         * of the constructor to use
         * @param parameterValues  the values to use to construct the current
         * instance of the object
         * @return the newly created object
         * @throws CreationException if unable to create the object
         */
        public T getObject(final Properties props,
                                 final Class[] parameterTypes,
                                 final Object[] parameterValues)
                throws CreationException {

            // Unit test override, do not use application instance.
            final String className = getClassName();
            if (className != null) {
                return getObject(className, parameterTypes, parameterValues);
            }

            // NOTE: Should we distinguish between any Properties Object
            // and that returned by System? When its the System's
            // Properties Object (which is not a final instance variable
            // within the System class), then its for sure the user
            // providing a global override. If its not the System
            // Properties object, then it may or may not be a global
            // override so we may not want to set the singleInstance
            // to it. For now I am ignoring the issue.
            if (this.singleInstance == null) {
                final String propClassName = getClassName(props);

                this.singleInstance = (propClassName != null)
                        // The user overriding application default
                    ? getObject(propClassName, parameterTypes, parameterValues)
                        // Get application default
                    : getDefault(parameterTypes, parameterValues);

            }
            return this.singleInstance;
        }
    }
}

// End ObjectFactory.java