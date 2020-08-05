package org.hisp.dhis.dxf2;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.lang.reflect.InvocationTargetException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.hisp.dhis.DhisSpringTest;
import org.hisp.dhis.category.CategoryCombo;
import org.hisp.dhis.category.CategoryOptionCombo;
import org.hisp.dhis.common.CodeGenerator;
import org.hisp.dhis.common.IdentifiableObjectManager;
import org.hisp.dhis.dbms.DbmsManager;
import org.hisp.dhis.dxf2.common.ImportOptions;
import org.hisp.dhis.dxf2.events.enrollment.Enrollment;
import org.hisp.dhis.dxf2.events.enrollment.EnrollmentService;
import org.hisp.dhis.dxf2.events.enrollment.EnrollmentStatus;
import org.hisp.dhis.dxf2.events.event.Event;
import org.hisp.dhis.dxf2.importsummary.ImportSummary;
import org.hisp.dhis.event.EventStatus;
import org.hisp.dhis.mock.MockCurrentUserService;
import org.hisp.dhis.organisationunit.OrganisationUnit;
import org.hisp.dhis.program.Program;
import org.hisp.dhis.program.ProgramStage;
import org.hisp.dhis.program.ProgramType;
import org.hisp.dhis.trackedentity.TrackedEntityInstance;
import org.hisp.dhis.trackedentity.TrackedEntityInstanceService;
import org.hisp.dhis.trackedentity.TrackedEntityType;
import org.hisp.dhis.trackedentity.TrackedEntityTypeService;
import org.hisp.dhis.user.CurrentUserService;
import org.hisp.dhis.user.User;
import org.hisp.dhis.user.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * @author Luciano Fiandesio
 */
public abstract class TrackerTest extends DhisSpringTest
{
    @Autowired
    protected IdentifiableObjectManager manager;

    @Autowired
    private TrackedEntityTypeService trackedEntityTypeService;

    @Autowired
    private EnrollmentService enrollmentService;

    @Autowired
    private TrackedEntityInstanceService trackedEntityInstanceService;

    @Autowired
    protected UserService userService;

    @Autowired
    private TransactionTemplate txTemplate;

    @Autowired
    protected DbmsManager dbmsManager;

    protected CurrentUserService currentUserService;

    protected TrackedEntityType trackedEntityTypeA;

    protected OrganisationUnit organisationUnitA;

    protected Program programA;

    protected ProgramStage programStageA1;

    protected CategoryCombo categoryComboA;

    protected final static String DEF_COC_UID = CodeGenerator.generateUid();

    @Override
    protected void setUpTest()
        throws Exception
    {
        // Tracker graph creation
        trackedEntityTypeA = createTrackedEntityType( 'A' );
        trackedEntityTypeA.setUid( CodeGenerator.generateUid() );
        trackedEntityTypeA.setName( "TrackedEntityTypeA" + trackedEntityTypeA.getUid() );

        organisationUnitA = createOrganisationUnit( 'A' );
        organisationUnitA.setUid( CodeGenerator.generateUid() );
        organisationUnitA.setCode( RandomStringUtils.randomAlphanumeric( 10 ) );

        categoryComboA = createCategoryCombo( 'A' );
        categoryComboA.setUid( CodeGenerator.generateUid() );
        categoryComboA.setName( RandomStringUtils.randomAlphanumeric( 10 ) );

        ProgramStage programStageA2;

        programStageA1 = createProgramStage( programA );
        programStageA2 = createProgramStage( programA );

        programA = createProgram( 'A', new HashSet<>(), organisationUnitA );
        programA.setProgramType( ProgramType.WITH_REGISTRATION );
        programA.setCategoryCombo( categoryComboA );
        programA.setUid( CodeGenerator.generateUid() );
        programA.setCode( RandomStringUtils.randomAlphanumeric( 10 ) );
        programA.setProgramStages(
            Stream.of( programStageA1, programStageA2 ).collect( Collectors.toCollection( HashSet::new ) ) );

        CategoryOptionCombo defaultCategoryOptionCombo = createCategoryOptionCombo( 'A' );
        defaultCategoryOptionCombo.setCategoryCombo( categoryComboA );
        defaultCategoryOptionCombo.setUid( DEF_COC_UID );
        defaultCategoryOptionCombo.setName( "default" );

        // Tracker graph persistence
        doInTransaction( () -> {

            trackedEntityTypeService.addTrackedEntityType( trackedEntityTypeA );

            manager.save( organisationUnitA );

            manager.save( categoryComboA );

            manager.save( defaultCategoryOptionCombo );

            manager.save( programA );
        } );

        super.userService = this.userService;

        mockCurrentUserService();
    }

    @Override
    public void tearDownTest()
    {

        dbmsManager.emptyDatabase();
    }

    public TrackedEntityInstance persistTrackedEntityInstance()
    {
        TrackedEntityInstance entityInstance = createTrackedEntityInstance( organisationUnitA );

        entityInstance.setTrackedEntityType( trackedEntityTypeA );

        // manager.save( entityInstance );
        trackedEntityInstanceService.addTrackedEntityInstance( entityInstance );
        return entityInstance;
    }

    public TrackedEntityInstance persistTrackedEntityInstanceWithEnrollment()
    {
        return _persistTrackedEntityInstanceWithEnrollmentAndEvents( 0, new HashMap<>() );
    }

    public TrackedEntityInstance persistTrackedEntityInstanceWithEnrollmentAndEvents()
    {
        return _persistTrackedEntityInstanceWithEnrollmentAndEvents( 5, new HashMap<>() );
    }

    public TrackedEntityInstance persistTrackedEntityInstanceWithEnrollmentAndEvents(
        Map<String, Object> enrollmentValues )
    {
        return _persistTrackedEntityInstanceWithEnrollmentAndEvents( 5, enrollmentValues );
    }

    private TrackedEntityInstance _persistTrackedEntityInstanceWithEnrollmentAndEvents( int eventSize, Map<String, Object> enrollmentValues )
    {
        TrackedEntityInstance entityInstance = persistTrackedEntityInstance();

        final ImportSummary importSummary = enrollmentService.addEnrollment(
            createEnrollmentWithEvents( this.programA, entityInstance, eventSize, enrollmentValues ),
            ImportOptions.getDefaultImportOptions() );

        assertThat( importSummary.getConflicts(), hasSize( 0 ) );

        assertThat( importSummary.getEvents().getImported(), is( eventSize ) );

        return entityInstance;
    }

    private Enrollment createEnrollmentWithEvents( Program program, TrackedEntityInstance trackedEntityInstance,
        int events )
    {
        Enrollment enrollment = new Enrollment();
        enrollment.setEnrollment( CodeGenerator.generateUid() );
        enrollment.setOrgUnit( organisationUnitA.getUid() );
        enrollment.setProgram( program.getUid() );
        enrollment.setTrackedEntityInstance( trackedEntityInstance.getUid() );
        enrollment.setEnrollmentDate( new Date() );
        enrollment.setStatus( EnrollmentStatus.COMPLETED );
        enrollment.setIncidentDate( new Date() );
        enrollment.setCompletedDate( new Date() );
        enrollment.setCompletedBy( "hello-world" );

        if ( events > 0 )
        {
            List<Event> eventList = new ArrayList<>();

            for ( int i = 0; i < events; i++ )
            {
                Event event1 = new Event();
                event1.setEnrollment( enrollment.getEnrollment() );
                event1.setEventDate(
                    DateTimeFormatter.ofPattern( "yyyy-MM-dd", Locale.ENGLISH ).format( LocalDateTime.now() ) );
                event1.setProgram( programA.getUid() );
                event1.setProgramStage( programStageA1.getUid() );
                event1.setStatus( EventStatus.COMPLETED );
                event1.setTrackedEntityInstance( trackedEntityInstance.getUid() );
                event1.setOrgUnit( organisationUnitA.getUid() );
                event1.setAttributeOptionCombo( categoryComboA.getUid() );

                eventList.add( event1 );
            }

            enrollment.setEvents( eventList );
        }
        return enrollment;
    }

    private Enrollment createEnrollmentWithEvents( Program program, TrackedEntityInstance trackedEntityInstance,
        int events, Map<String, Object> enrollmentValues )
    {
        Enrollment enrollment = createEnrollmentWithEvents( program, trackedEntityInstance, events );

        if ( enrollmentValues != null && !enrollmentValues.isEmpty() )
        {
            for ( String method : enrollmentValues.keySet() )
            {
                try
                {
                    BeanUtils.setProperty( enrollment, method, enrollmentValues.get( method ) );
                }
                catch ( IllegalAccessException | InvocationTargetException e )
                {
                    fail( e.getMessage() );
                }
            }
        }
        
        return enrollment;
    }

    protected void mockCurrentUserService()
    {
        User user = createUser( "testUser" );
        currentUserService = new MockCurrentUserService( user );
    }

    private ProgramStage createProgramStage( Program program )
    {
        ProgramStage programStage = createProgramStage( '1', program );
        programStage.setUid( CodeGenerator.generateUid() );
        programStage.setRepeatable( true );
        doInTransaction( () -> manager.save( programStage ) );

        return programStage;
    }

    protected void doInTransaction( Runnable operation )
    {
        final int defaultPropagationBehaviour = txTemplate.getPropagationBehavior();
        txTemplate.setPropagationBehavior( TransactionDefinition.PROPAGATION_REQUIRES_NEW );

        txTemplate.execute( status -> {

            operation.run();

            return null;
        } );
        // restore original propagation behaviour
        txTemplate.setPropagationBehavior( defaultPropagationBehaviour );
    }

}
