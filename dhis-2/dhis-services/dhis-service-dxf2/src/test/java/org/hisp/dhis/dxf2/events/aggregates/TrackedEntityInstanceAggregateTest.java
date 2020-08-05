package org.hisp.dhis.dxf2.events.aggregates;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hisp.dhis.matchers.DateTimeFormatMatcher.hasDateTimeFormat;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hisp.dhis.common.CodeGenerator;
import org.hisp.dhis.dxf2.TrackerTest;
import org.hisp.dhis.dxf2.events.TrackedEntityInstanceParams;
import org.hisp.dhis.dxf2.events.enrollment.Enrollment;
import org.hisp.dhis.dxf2.events.enrollment.EnrollmentStatus;
import org.hisp.dhis.dxf2.events.event.Event;
import org.hisp.dhis.dxf2.events.trackedentity.ProgramOwner;
import org.hisp.dhis.dxf2.events.trackedentity.Relationship;
import org.hisp.dhis.dxf2.events.trackedentity.TrackedEntityInstance;
import org.hisp.dhis.dxf2.events.trackedentity.TrackedEntityInstanceService;
import org.hisp.dhis.event.EventStatus;
import org.hisp.dhis.mock.MockCurrentUserService;
import org.hisp.dhis.organisationunit.FeatureType;
import org.hisp.dhis.program.ProgramInstance;
import org.hisp.dhis.program.ProgramStageInstance;
import org.hisp.dhis.trackedentity.TrackedEntityInstanceQueryParams;
import org.hisp.dhis.trackedentity.TrackedEntityProgramOwnerService;
import org.hisp.dhis.user.User;
import org.hisp.dhis.user.UserAuthorityGroup;
import org.hisp.dhis.user.UserCredentials;
import org.hisp.dhis.util.DateUtils;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;

import com.google.common.collect.Sets;

/**
 * @author Luciano Fiandesio
 */
public class TrackedEntityInstanceAggregateTest extends TrackerTest
{
    @Autowired
    private TrackedEntityInstanceService trackedEntityInstanceService;

    @Autowired
    private TrackedEntityInstanceAggregate trackedEntityInstanceAggregate;

    @Autowired
    private TrackedEntityProgramOwnerService programOwnerService;

    private final static String DATE_TIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS";

    @Override
    protected void mockCurrentUserService()
    {
        User user = createUser( "testUser" );

        makeUserSuper( user );

        currentUserService = new MockCurrentUserService( user );
    }

    @Before
    public void setUp()
    {
        ReflectionTestUtils.setField( trackedEntityInstanceAggregate, "currentUserService", currentUserService );
    }

    @Test
    public void testFetchTrackedEntityInstances()
    {
        doInTransaction( () -> {
            this.persistTrackedEntityInstance();
            this.persistTrackedEntityInstance();
            this.persistTrackedEntityInstance();
            this.persistTrackedEntityInstance();
        } );

        TrackedEntityInstanceQueryParams queryParams = new TrackedEntityInstanceQueryParams();
        queryParams.setOrganisationUnits( Sets.newHashSet( organisationUnitA ) );
        queryParams.setIncludeAllAttributes( true );

        TrackedEntityInstanceParams params = new TrackedEntityInstanceParams();

        final List<TrackedEntityInstance> trackedEntityInstances = trackedEntityInstanceService
            .getTrackedEntityInstances2( queryParams, params, false );

        assertThat( trackedEntityInstances, hasSize( 4 ) );
        assertThat( trackedEntityInstances.get( 0 ).getEnrollments(), hasSize( 0 ) );

    }

    @Test
    public void testFetchTrackedEntityInstancesAndEnrollments()
    {
        doInTransaction( () -> {
            this.persistTrackedEntityInstanceWithEnrollment();
            this.persistTrackedEntityInstanceWithEnrollment();
            this.persistTrackedEntityInstanceWithEnrollment();
            this.persistTrackedEntityInstanceWithEnrollment();
        } );

        TrackedEntityInstanceQueryParams queryParams = new TrackedEntityInstanceQueryParams();
        queryParams.setOrganisationUnits( Sets.newHashSet( organisationUnitA ) );
        queryParams.setIncludeAllAttributes( true );

        TrackedEntityInstanceParams params = new TrackedEntityInstanceParams();
        params.setIncludeEnrollments( true );

        final List<TrackedEntityInstance> trackedEntityInstances = trackedEntityInstanceService
            .getTrackedEntityInstances2( queryParams, params, false );

        assertThat( trackedEntityInstances, hasSize( 4 ) );

        assertThat( trackedEntityInstances.get( 0 ).getEnrollments(), hasSize( 1 ) );
        assertThat( trackedEntityInstances.get( 1 ).getEnrollments(), hasSize( 1 ) );
        assertThat( trackedEntityInstances.get( 2 ).getEnrollments(), hasSize( 1 ) );
        assertThat( trackedEntityInstances.get( 3 ).getEnrollments(), hasSize( 1 ) );

    }

    @Test
    public void testFetchTrackedEntityInstancesWithoutEnrollments()
    {
        doInTransaction( () -> {
            this.persistTrackedEntityInstanceWithEnrollment();
            this.persistTrackedEntityInstanceWithEnrollment();
            this.persistTrackedEntityInstanceWithEnrollment();
            this.persistTrackedEntityInstanceWithEnrollment();
        } );

        TrackedEntityInstanceQueryParams queryParams = new TrackedEntityInstanceQueryParams();
        queryParams.setOrganisationUnits( Sets.newHashSet( organisationUnitA ) );
        queryParams.setIncludeAllAttributes( true );

        TrackedEntityInstanceParams params = new TrackedEntityInstanceParams();
        params.setIncludeEnrollments( false );

        final List<TrackedEntityInstance> trackedEntityInstances = trackedEntityInstanceService
            .getTrackedEntityInstances2( queryParams, params, false );

        assertThat( trackedEntityInstances, hasSize( 4 ) );

        assertThat( trackedEntityInstances.get( 0 ).getEnrollments(), hasSize( 0 ) );
        assertThat( trackedEntityInstances.get( 1 ).getEnrollments(), hasSize( 0 ) );
        assertThat( trackedEntityInstances.get( 2 ).getEnrollments(), hasSize( 0 ) );
        assertThat( trackedEntityInstances.get( 3 ).getEnrollments(), hasSize( 0 ) );

    }

    @Test
    public void testFetchTrackedEntityInstancesWithEvents()
    {
        doInTransaction( () -> {
            this.persistTrackedEntityInstanceWithEnrollmentAndEvents();
            this.persistTrackedEntityInstanceWithEnrollmentAndEvents();
            this.persistTrackedEntityInstanceWithEnrollmentAndEvents();
            this.persistTrackedEntityInstanceWithEnrollmentAndEvents();
        } );

        TrackedEntityInstanceQueryParams queryParams = new TrackedEntityInstanceQueryParams();
        queryParams.setOrganisationUnits( Sets.newHashSet( organisationUnitA ) );
        queryParams.setIncludeAllAttributes( true );

        TrackedEntityInstanceParams params = new TrackedEntityInstanceParams();
        params.setIncludeEnrollments( true );
        params.setIncludeEvents( true );

        final List<TrackedEntityInstance> trackedEntityInstances = trackedEntityInstanceService
            .getTrackedEntityInstances2( queryParams, params, false );

        assertThat( trackedEntityInstances, hasSize( 4 ) );

        assertThat( trackedEntityInstances.get( 0 ).getEnrollments(), hasSize( 1 ) );
        assertThat( trackedEntityInstances.get( 1 ).getEnrollments(), hasSize( 1 ) );
        assertThat( trackedEntityInstances.get( 2 ).getEnrollments(), hasSize( 1 ) );
        assertThat( trackedEntityInstances.get( 3 ).getEnrollments(), hasSize( 1 ) );

        assertThat( trackedEntityInstances.get( 0 ).getEnrollments().get( 0 ).getEvents(), hasSize( 5 ) );
        assertThat( trackedEntityInstances.get( 1 ).getEnrollments().get( 0 ).getEvents(), hasSize( 5 ) );
        assertThat( trackedEntityInstances.get( 2 ).getEnrollments().get( 0 ).getEvents(), hasSize( 5 ) );
        assertThat( trackedEntityInstances.get( 3 ).getEnrollments().get( 0 ).getEvents(), hasSize( 5 ) );
    }

    @Test
    public void testFetchTrackedEntityInstancesWithoutEvents()
    {
        doInTransaction( () -> {
            this.persistTrackedEntityInstanceWithEnrollmentAndEvents();
            this.persistTrackedEntityInstanceWithEnrollmentAndEvents();
            this.persistTrackedEntityInstanceWithEnrollmentAndEvents();
            this.persistTrackedEntityInstanceWithEnrollmentAndEvents();
        } );

        TrackedEntityInstanceQueryParams queryParams = new TrackedEntityInstanceQueryParams();
        queryParams.setOrganisationUnits( Sets.newHashSet( organisationUnitA ) );
        queryParams.setIncludeAllAttributes( true );

        TrackedEntityInstanceParams params = new TrackedEntityInstanceParams();
        params.setIncludeEnrollments( true );
        params.setIncludeEvents( false );

        final List<TrackedEntityInstance> trackedEntityInstances = trackedEntityInstanceService
            .getTrackedEntityInstances2( queryParams, params, false );

        assertThat( trackedEntityInstances, hasSize( 4 ) );

        assertThat( trackedEntityInstances.get( 0 ).getEnrollments(), hasSize( 1 ) );
        assertThat( trackedEntityInstances.get( 1 ).getEnrollments(), hasSize( 1 ) );
        assertThat( trackedEntityInstances.get( 2 ).getEnrollments(), hasSize( 1 ) );
        assertThat( trackedEntityInstances.get( 3 ).getEnrollments(), hasSize( 1 ) );

        assertThat( trackedEntityInstances.get( 0 ).getEnrollments().get( 0 ).getEvents(), hasSize( 0 ) );
        assertThat( trackedEntityInstances.get( 1 ).getEnrollments().get( 0 ).getEvents(), hasSize( 0 ) );
        assertThat( trackedEntityInstances.get( 2 ).getEnrollments().get( 0 ).getEvents(), hasSize( 0 ) );
        assertThat( trackedEntityInstances.get( 3 ).getEnrollments().get( 0 ).getEvents(), hasSize( 0 ) );
    }

    @Test
    public void testTrackedEntityInstanceMapping()
    {
        final Date currentTime = new Date();

        doInTransaction( this::persistTrackedEntityInstanceWithEnrollmentAndEvents );

        TrackedEntityInstanceQueryParams queryParams = new TrackedEntityInstanceQueryParams();
        queryParams.setOrganisationUnits( Sets.newHashSet( organisationUnitA ) );
        queryParams.setIncludeAllAttributes( true );

        TrackedEntityInstanceParams params = new TrackedEntityInstanceParams();
        params.setIncludeEnrollments( false );
        params.setIncludeEvents( false );

        final List<TrackedEntityInstance> trackedEntityInstances = trackedEntityInstanceService
            .getTrackedEntityInstances2( queryParams, params, false );

        TrackedEntityInstance trackedEntityInstance = trackedEntityInstances.get( 0 );

        assertThat( trackedEntityInstance.getTrackedEntityType(), is( trackedEntityTypeA.getUid() ) );
        assertTrue( CodeGenerator.isValidUid( trackedEntityInstance.getTrackedEntityInstance() ) );
        assertThat( trackedEntityInstance.getOrgUnit(), is( organisationUnitA.getUid() ) );
        assertThat( trackedEntityInstance.isInactive(), is( false ) );
        assertThat( trackedEntityInstance.isDeleted(), is( false ) );
        assertThat( trackedEntityInstance.getFeatureType(), is( FeatureType.NONE ) );

        // Dates

        checkDate( currentTime, trackedEntityInstance.getCreated(), 50L );
        checkDate( currentTime, trackedEntityInstance.getCreatedAtClient(), 50L );
        checkDate( currentTime, trackedEntityInstance.getLastUpdatedAtClient(), 50L );
        checkDate( currentTime, trackedEntityInstance.getLastUpdated(), 300L );

        // get stored by is always null
        assertThat( trackedEntityInstance.getStoredBy(), is( nullValue() ) );
    }

    @Test
    public void testEventMapping()
    {
        final Date currentTime = new Date();

        doInTransaction( this::persistTrackedEntityInstanceWithEnrollmentAndEvents );

        TrackedEntityInstanceQueryParams queryParams = new TrackedEntityInstanceQueryParams();
        queryParams.setOrganisationUnits( Sets.newHashSet( organisationUnitA ) );
        queryParams.setIncludeAllAttributes( true );

        TrackedEntityInstanceParams params = new TrackedEntityInstanceParams();
        params.setIncludeEnrollments( true );
        params.setIncludeEvents( true );

        final List<TrackedEntityInstance> trackedEntityInstances = trackedEntityInstanceService
            .getTrackedEntityInstances2( queryParams, params, false );
        TrackedEntityInstance tei = trackedEntityInstances.get( 0 );
        Enrollment enrollment = tei.getEnrollments().get( 0 );
        Event event = enrollment.getEvents().get( 0 );

        assertNotNull( event );

        // The id is not serialized to JSON
        assertThat( event.getId(), is( notNullValue() ) );
        assertThat( event.getUid(), is( nullValue() ) );
        assertTrue( CodeGenerator.isValidUid( event.getEvent() ) );
        assertThat( event.getStatus(), is( EventStatus.COMPLETED ) );
        assertThat( event.getProgram(), is( programA.getUid() ) );
        assertThat( event.getProgramStage(), is( programStageA1.getUid() ) );
        assertThat( event.getEnrollment(), is( enrollment.getEnrollment() ) );
        assertThat( event.getEnrollmentStatus(), is( enrollment.getStatus() ) );
        assertThat( event.getOrgUnit(), is( organisationUnitA.getUid() ) );
        assertThat( event.getOrgUnitName(), is( organisationUnitA.getName() ) );
        assertThat( event.getTrackedEntityInstance(), is( tei.getTrackedEntityInstance() ) );
        assertThat( event.getAttributeOptionCombo(), is( DEF_COC_UID ) );

        assertThat( event.isDeleted(), is( false ) );
        assertThat( event.getStoredBy(), is( "[Unknown]" ) );
        assertThat( event.getFollowup(), is( nullValue() ) );

        // Dates
        checkDate( currentTime, event.getCreated(), 400L );
        checkDate( currentTime, event.getLastUpdated(), 300L );
        assertThat( event.getEventDate(), is( notNullValue() ) );
        checkDate( currentTime, event.getDueDate(), 400L );
        checkDate( currentTime, event.getCreatedAtClient(), 400L );
        checkDate( currentTime, event.getLastUpdatedAtClient(), 400L );
        checkDate( currentTime, event.getCompletedDate(), 400L );
        assertThat( event.getCompletedBy(), is( "[Unknown]" ) );
    }

    @Test
    public void testEnrollmentMapping()
    {
        final Date currentTime = new Date();

        doInTransaction( this::persistTrackedEntityInstanceWithEnrollmentAndEvents );

        TrackedEntityInstanceQueryParams queryParams = new TrackedEntityInstanceQueryParams();
        queryParams.setOrganisationUnits( Sets.newHashSet( organisationUnitA ) );
        queryParams.setIncludeAllAttributes( true );

        TrackedEntityInstanceParams params = new TrackedEntityInstanceParams();
        params.setIncludeEnrollments( true );
        params.setIncludeEvents( false );

        final List<TrackedEntityInstance> trackedEntityInstances = trackedEntityInstanceService
                .getTrackedEntityInstances2( queryParams, params, false );

        Enrollment enrollment = trackedEntityInstances.get( 0 ).getEnrollments().get( 0 );

        assertThat( "Tracked Entity Type does not match", enrollment.getTrackedEntityType(),
                is( trackedEntityTypeA.getUid() ) );
        assertThat( "Tracked Entity Instance UID does not match", enrollment.getTrackedEntityInstance(),
                is( trackedEntityInstances.get( 0 ).getTrackedEntityInstance() ) );
        assertThat( "Org Unit UID does not match", enrollment.getOrgUnit(), is( organisationUnitA.getUid() ) );
        assertThat( "Org Unit Name does not match", enrollment.getOrgUnitName(), is( organisationUnitA.getName() ) );
        assertTrue( CodeGenerator.isValidUid( enrollment.getEnrollment() ) );
        assertThat( enrollment.getProgram(), is( programA.getUid() ) );
        assertThat( enrollment.getStatus(), is( EnrollmentStatus.COMPLETED ) );
        assertThat( enrollment.isDeleted(), is( false ) );
        assertThat( enrollment.getStoredBy(), is( "system-process" ) );
        assertThat( enrollment.getFollowup(), is( nullValue() ) );

        // Dates

        checkDate( currentTime, enrollment.getCreated(), 200L );
        checkDate( currentTime, enrollment.getCreatedAtClient(), 200L );
        checkDate( currentTime, enrollment.getLastUpdatedAtClient(), 200L );
        checkDate( currentTime, enrollment.getLastUpdated(), 300L );
        checkDate( currentTime, enrollment.getEnrollmentDate(), 300L );
        checkDate( currentTime, enrollment.getIncidentDate(), 300L );
        checkDate( currentTime, enrollment.getCompletedDate(), 200L );
        assertThat( enrollment.getCompletedBy(), is( "hello-world" ) );

        // The Enrollment ID is not serialized to JSON
        assertThat( enrollment.getId(), is( notNullValue() ) );
    }

    @Test
    public void testEnrollmentFollowup()
    {
        Map<String, Object> enrollmentValues = new HashMap<>();
        enrollmentValues.put( "followup", true );
        doInTransaction( () -> this.persistTrackedEntityInstanceWithEnrollmentAndEvents( enrollmentValues ) );

        TrackedEntityInstanceQueryParams queryParams = new TrackedEntityInstanceQueryParams();
        queryParams.setOrganisationUnits( Sets.newHashSet( organisationUnitA ) );
        queryParams.setIncludeAllAttributes( true );

        TrackedEntityInstanceParams params = new TrackedEntityInstanceParams();
        params.setIncludeEnrollments( true );
        params.setIncludeEvents( true );

        final List<TrackedEntityInstance> trackedEntityInstances = trackedEntityInstanceService
            .getTrackedEntityInstances2( queryParams, params, false );
        TrackedEntityInstance tei = trackedEntityInstances.get( 0 );
        Enrollment enrollment = tei.getEnrollments().get( 0 );
        Event event = enrollment.getEvents().get( 0 );

        assertThat( enrollment.getFollowup(), is( true ) );
        assertThat( event.getFollowup(), is( true ) );
    }

    @Test
    public void testEnrollmentWithoutOrgUnit()
    {
        Map<String, Object> enrollmentValues = new HashMap<>();
        enrollmentValues.put( "orgUnit", null );
        doInTransaction( () -> this.persistTrackedEntityInstanceWithEnrollmentAndEvents( enrollmentValues ) );

        TrackedEntityInstanceQueryParams queryParams = new TrackedEntityInstanceQueryParams();
        queryParams.setOrganisationUnits( Sets.newHashSet( organisationUnitA ) );
        queryParams.setIncludeAllAttributes( true );

        TrackedEntityInstanceParams params = new TrackedEntityInstanceParams();
        params.setIncludeEnrollments( true );
        params.setIncludeEvents( true );

        final List<TrackedEntityInstance> trackedEntityInstances = trackedEntityInstanceService
                .getTrackedEntityInstances2( queryParams, params, false );
        TrackedEntityInstance tei = trackedEntityInstances.get( 0 );
        Enrollment enrollment = tei.getEnrollments().get( 0 );
        Event event = enrollment.getEvents().get( 0 );
        
        assertNotNull( enrollment );
        assertNotNull( event );

    }

    @Test
    public void testTrackedEntityInstanceRelationshipsTei2Tei()
    {
        final String[] teiUid = new String[2];

        doInTransaction( () -> {
            org.hisp.dhis.trackedentity.TrackedEntityInstance t1 = this.persistTrackedEntityInstance();
            org.hisp.dhis.trackedentity.TrackedEntityInstance t2 = this.persistTrackedEntityInstance();
            this.persistRelationship( t1, t2 );
            teiUid[0] = t1.getUid();
            teiUid[1] = t2.getUid();
        } );

        TrackedEntityInstanceQueryParams queryParams = new TrackedEntityInstanceQueryParams();
        queryParams.setOrganisationUnits( Sets.newHashSet( organisationUnitA ) );
        queryParams.setIncludeAllAttributes( true );

        TrackedEntityInstanceParams params = new TrackedEntityInstanceParams();
        params.setIncludeRelationships( true );

        final List<TrackedEntityInstance> trackedEntityInstances = trackedEntityInstanceService
            .getTrackedEntityInstances2( queryParams, params, false );

        assertThat( trackedEntityInstances.get( 0 ).getRelationships(), hasSize( 1 ) );
        final Relationship relationship = trackedEntityInstances.get( 0 ).getRelationships().get( 0 );

        assertThat( relationship.getFrom().getTrackedEntityInstance().getTrackedEntityInstance(), is( teiUid[0] ) );
        assertThat( relationship.getTo().getTrackedEntityInstance().getTrackedEntityInstance(), is( teiUid[1] ) );
    }

    @Test
    public void testTrackedEntityInstanceRelationshipsTei2Enrollment()
    {
        final String[] relationshipItemsUid = new String[2];

        doInTransaction( () -> {
            org.hisp.dhis.trackedentity.TrackedEntityInstance t1 = this.persistTrackedEntityInstance();
            org.hisp.dhis.trackedentity.TrackedEntityInstance t2 = this.persistTrackedEntityInstanceWithEnrollment();
            ProgramInstance pi = t2.getProgramInstances().iterator().next();
            this.persistRelationship( t1, pi );
            relationshipItemsUid[0] = t1.getUid();
            relationshipItemsUid[1] = pi.getUid();
        } );

        TrackedEntityInstanceQueryParams queryParams = new TrackedEntityInstanceQueryParams();
        queryParams.setOrganisationUnits( Sets.newHashSet( organisationUnitA ) );
        queryParams.setIncludeAllAttributes( true );

        TrackedEntityInstanceParams params = new TrackedEntityInstanceParams();
        params.setIncludeRelationships( true );

        final List<TrackedEntityInstance> trackedEntityInstances = trackedEntityInstanceService
            .getTrackedEntityInstances2( queryParams, params, false );

        // Fetch the TEI which is the vertex of the relationship TEI <--> ENROLLMENT
        TrackedEntityInstance trackedEntityInstance = trackedEntityInstances.stream()
            .filter( t -> t.getTrackedEntityInstance().equals( relationshipItemsUid[0] ) ).findFirst().get();

        assertThat( trackedEntityInstance.getRelationships(), hasSize( 1 ) );

        final Relationship relationship = trackedEntityInstance.getRelationships().get( 0 );

        assertThat( relationship.getFrom().getTrackedEntityInstance().getTrackedEntityInstance(),
            is( relationshipItemsUid[0] ) );
        assertThat( relationship.getTo().getEnrollment().getEnrollment(), is( relationshipItemsUid[1] ) );
    }

    @Test
    public void testTrackedEntityInstanceRelationshipsTei2Event()
    {
        final String[] relationshipItemsUid = new String[2];

        doInTransaction( () -> {
            org.hisp.dhis.trackedentity.TrackedEntityInstance t1 = this.persistTrackedEntityInstance();
            org.hisp.dhis.trackedentity.TrackedEntityInstance t2 = this
                .persistTrackedEntityInstanceWithEnrollmentAndEvents();
            ProgramInstance pi = t2.getProgramInstances().iterator().next();
            final ProgramStageInstance psi = pi.getProgramStageInstances().iterator().next();
            this.persistRelationship( t1, psi );
            relationshipItemsUid[0] = t1.getUid();
            relationshipItemsUid[1] = psi.getUid();
        } );

        TrackedEntityInstanceQueryParams queryParams = new TrackedEntityInstanceQueryParams();
        queryParams.setOrganisationUnits( Sets.newHashSet( organisationUnitA ) );
        queryParams.setIncludeAllAttributes( true );

        TrackedEntityInstanceParams params = new TrackedEntityInstanceParams();
        params.setIncludeRelationships( true );

        final List<TrackedEntityInstance> trackedEntityInstances = trackedEntityInstanceService
            .getTrackedEntityInstances2( queryParams, params, false );

        // Fetch the TEI which is the vertex of the relationship TEI <--> ENROLLMENT
        TrackedEntityInstance trackedEntityInstance = trackedEntityInstances.stream()
            .filter( t -> t.getTrackedEntityInstance().equals( relationshipItemsUid[0] ) ).findFirst().get();

        assertThat( trackedEntityInstance.getRelationships(), hasSize( 1 ) );

        final Relationship relationship = trackedEntityInstance.getRelationships().get( 0 );

        assertThat( relationship.getFrom().getTrackedEntityInstance().getTrackedEntityInstance(),
            is( relationshipItemsUid[0] ) );
        assertThat( relationship.getTo().getEvent().getEvent(), is( relationshipItemsUid[1] ) );

    }

    @Test
    public void testTrackedEntityInstanceProgramOwners()
    {
        doInTransaction( () -> {
            final org.hisp.dhis.trackedentity.TrackedEntityInstance trackedEntityInstance = persistTrackedEntityInstance();
            programOwnerService.createOrUpdateTrackedEntityProgramOwner( trackedEntityInstance, programA,
                organisationUnitA );
        } );

        TrackedEntityInstanceQueryParams queryParams = new TrackedEntityInstanceQueryParams();
        queryParams.setOrganisationUnits( Sets.newHashSet( organisationUnitA ) );
        queryParams.setIncludeAllAttributes( true );

        TrackedEntityInstanceParams params = new TrackedEntityInstanceParams();
        params.setIncludeProgramOwners( true );

        final List<TrackedEntityInstance> trackedEntityInstances = trackedEntityInstanceService
            .getTrackedEntityInstances2( queryParams, params, false );

        assertThat( trackedEntityInstances.get( 0 ).getProgramOwners(), hasSize( 1 ) );
        ProgramOwner programOwner = trackedEntityInstances.get( 0 ).getProgramOwners().get( 0 );
        assertThat( programOwner.getProgram(), is( programA.getUid() ) );
        assertThat( programOwner.getOwnerOrgUnit(), is( organisationUnitA.getUid() ) );
        assertThat( programOwner.getTrackedEntityInstance(), is(  trackedEntityInstances.get( 0 ).getTrackedEntityInstance() ) );
    }


    private void checkDate( Date currentTime, String date, long milliseconds )
    {
        final long interval = currentTime.getTime() - DateUtils.parseDate( date ).getTime();
        assertThat( date, hasDateTimeFormat( DATE_TIME_FORMAT ) );
        assertTrue(
            "Timestamp is higher than expected interval. Expecting: " + milliseconds + " got: " + interval,
            Math.abs( interval ) < milliseconds );
    }

    private void checkDate( Date currentTime, Date date, long milliseconds )
    {
        final long interval = currentTime.getTime() - date.getTime();
        assertTrue(
            "Timestamp is higher than expected interval. Expecting: " + milliseconds + " got: " + interval,
            Math.abs( interval ) < milliseconds );
    }

    private void makeUserSuper( User user )
    {
        UserCredentials userCredentials = new UserCredentials();
        UserAuthorityGroup userAuthorityGroup1Super = new UserAuthorityGroup();
        userAuthorityGroup1Super.setUid( "uid4" );
        userAuthorityGroup1Super
            .setAuthorities( new HashSet<>( Arrays.asList( "z1", UserAuthorityGroup.AUTHORITY_ALL ) ) );
        userCredentials.setUserAuthorityGroups( Sets.newHashSet( userAuthorityGroup1Super ) );
        user.setUserCredentials( userCredentials );
    }
}