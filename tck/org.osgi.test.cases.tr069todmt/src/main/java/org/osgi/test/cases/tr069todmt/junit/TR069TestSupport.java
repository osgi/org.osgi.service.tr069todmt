/*******************************************************************************
 * Copyright (c) Contributors to the Eclipse Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 *******************************************************************************/

package org.osgi.test.cases.tr069todmt.junit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Map;

import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.test.common.annotation.InjectBundleContext;

/**
 * The helpers these tests used from DefaultTestBundleControl and
 * OSGiTestCase, unchanged: registered and fetched services are remembered by
 * their service object, so they can be released by it.
 */
abstract class TR069TestSupport {
	@InjectBundleContext
	protected BundleContext								context;

	private final Map<Object,ServiceRegistration< ? >>	serviceRegistry	= new HashMap<>();
	private final Map<Object,ServiceReference< ? >>		fetchedServices	= new HashMap<>();

	public void registerService(String clazz, Object service,
			Dictionary<String, ? > properties) throws Exception {
		ServiceRegistration< ? > sr = context.registerService(clazz, service,
				properties);
		synchronized (serviceRegistry) {
			serviceRegistry.put(service, sr);
		}
	}

	public void unregisterAllServices() {
		ServiceRegistration< ? >[] srs;
		synchronized (serviceRegistry) {
			srs = serviceRegistry.values()
					.toArray(new ServiceRegistration[serviceRegistry.size()]);
			serviceRegistry.clear();
		}
		for (int l = srs.length, i = 0; i < l; i++) {
			srs[i].unregister();
		}
	}

	public <S> S getService(Class<S> clazz) {
		try {
			return getService(clazz, null);
		} catch (InvalidSyntaxException e) {
			/* null can not give an exception of this type */
		}
		return null;
	}

	public <S> S getService(Class<S> clazz, String filter)
			throws InvalidSyntaxException {
		@SuppressWarnings("unchecked")
		ServiceReference<S>[] refs = (ServiceReference<S>[]) context
				.getServiceReferences(clazz.getName(), filter);
		if (refs == null) {
			fail("Can't get service reference for " + clazz.getName() + filter);
		}
		ServiceReference<S> chosenRef = refs[0];
		/* highest ranking, then lowest service id: the ServiceReference order */
		for (int i = 1; i < refs.length; i++) {
			if (refs[i].compareTo(chosenRef) > 0) {
				chosenRef = refs[i];
			}
		}
		S service = context.getService(chosenRef);
		if (service == null) {
			fail("Can't get service for " + clazz.getName() + filter);
		}
		synchronized (fetchedServices) {
			fetchedServices.put(service, chosenRef);
		}
		return service;
	}

	public void ungetAllServices() {
		ServiceReference< ? >[] refs;
		synchronized (fetchedServices) {
			refs = fetchedServices.values()
					.toArray(new ServiceReference[fetchedServices.size()]);
			fetchedServices.clear();
		}
		for (int l = refs.length, i = 0; i < l; i++) {
			context.ungetService(refs[i]);
		}
	}

	/**
	 * Assert a constant from class has a specific value.
	 *
	 * @param expected Expected value.
	 * @param fieldName Name of constant.
	 * @param fieldClass Class containing constant.
	 */
	public static void assertConstant(Object expected, String fieldName,
			Class< ? > fieldClass) {
		try {
			Field f = fieldClass.getField(fieldName);
			assertThat(Modifier.isPublic(f.getModifiers())).isTrue();
			assertThat(Modifier.isStatic(f.getModifiers())).isTrue();
			assertThat(Modifier.isFinal(f.getModifiers())).isTrue();
			assertThat(f.get(null)).as(fieldName).isEqualTo(expected);
		} catch (NoSuchFieldException e) {
			fail("missing field: " + fieldName, e);
		} catch (IllegalAccessException e) {
			fail("bad field: " + fieldName, e);
		}
	}
}
