package com.github.mtakaki.credentialstorage.hibernate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

import com.google.common.collect.ImmutableList;

import io.dropwizard.hibernate.ScanningHibernateBundle;

public class ScanningHibernateBundleTest {

    @Test
    public void testFindEntityClassesFromDirectory() {
        //given
        String packageWithEntities = "io.dropwizard.hibernate.fake.entities.pckg";
        //when
        ImmutableList<Class<?>> findEntityClassesFromDirectory = ScanningHibernateBundle.findEntityClassesFromDirectory(packageWithEntities);

        //then
        assertFalse(findEntityClassesFromDirectory.isEmpty());
        assertEquals(4, findEntityClassesFromDirectory.size());
    }
}
