package org.hisp.dhis.webapi.controller.event.mapper;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;

import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.hisp.dhis.common.AssignedUserSelectionMode;
import org.hisp.dhis.common.DimensionalObject;
import org.hisp.dhis.common.IllegalQueryException;
import org.hisp.dhis.common.OrganisationUnitSelectionMode;
import org.hisp.dhis.common.PagerUtils;
import org.hisp.dhis.common.QueryFilter;
import org.hisp.dhis.common.QueryItem;
import org.hisp.dhis.common.QueryOperator;
import org.hisp.dhis.organisationunit.OrganisationUnit;
import org.hisp.dhis.organisationunit.OrganisationUnitService;
import org.hisp.dhis.program.Program;
import org.hisp.dhis.program.ProgramService;
import org.hisp.dhis.trackedentity.TrackedEntityAttribute;
import org.hisp.dhis.trackedentity.TrackedEntityAttributeService;
import org.hisp.dhis.trackedentity.TrackedEntityInstanceQueryParams;
import org.hisp.dhis.trackedentity.TrackedEntityType;
import org.hisp.dhis.trackedentity.TrackedEntityTypeService;
import org.hisp.dhis.user.CurrentUserService;
import org.hisp.dhis.user.User;
import org.hisp.dhis.webapi.controller.event.TrackedEntityInstanceCriteria;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author Luciano Fiandesio
 */
@Component
public class TrackedEntityCriteriaMapper
{
    private final CurrentUserService currentUserService;

    private final OrganisationUnitService organisationUnitService;

    private final ProgramService programService;

    private final TrackedEntityTypeService trackedEntityTypeService;

    private final TrackedEntityAttributeService attributeService;

    public TrackedEntityCriteriaMapper( CurrentUserService currentUserService,
        OrganisationUnitService organisationUnitService, ProgramService programService,
        TrackedEntityAttributeService attributeService, TrackedEntityTypeService trackedEntityTypeService )
    {
        checkNotNull( currentUserService );
        checkNotNull( organisationUnitService );
        checkNotNull( programService );
        checkNotNull( attributeService );
        checkNotNull( trackedEntityTypeService );

        this.currentUserService = currentUserService;
        this.organisationUnitService = organisationUnitService;
        this.programService = programService;
        this.attributeService = attributeService;
        this.trackedEntityTypeService = trackedEntityTypeService;
    }

    @Transactional( readOnly = true )
    public TrackedEntityInstanceQueryParams map( TrackedEntityInstanceCriteria criteria )
    {
        TrackedEntityInstanceQueryParams params = new TrackedEntityInstanceQueryParams();

        final Date programEnrollmentStartDate = ObjectUtils.firstNonNull( criteria.getProgramEnrollmentStartDate(),
            criteria.getProgramEndDate() );
        final Date programEnrollmentEndDate = ObjectUtils.firstNonNull( criteria.getProgramEnrollmentEndDate(),
            criteria.getProgramEndDate() );

        Set<OrganisationUnit> possibleSearchOrgUnits = new HashSet<>();

        User user = currentUserService.getCurrentUser();

        if ( user != null )
        {
            possibleSearchOrgUnits = user.getTeiSearchOrganisationUnitsWithFallback();
        }

        QueryFilter queryFilter = getQueryFilter( criteria.getQuery() );

        if ( criteria.getAttribute() != null )
        {
            for ( String attr : criteria.getAttribute() )
            {
                QueryItem it = getQueryItem( attr );

                params.getAttributes().add( it );
            }
        }

        if ( criteria.getFilter() != null )
        {
            for ( String filt : criteria.getFilter() )
            {
                QueryItem it = getQueryItem( filt );

                params.getFilters().add( it );
            }
        }

        for ( String orgUnit : criteria.getOrgUnits() )
        {
            OrganisationUnit organisationUnit = organisationUnitService.getOrganisationUnit( orgUnit );

            if ( organisationUnit == null )
            {
                throw new IllegalQueryException( "Organisation unit does not exist: " + orgUnit );
            }

            if ( !organisationUnitService.isInUserHierarchy( organisationUnit.getUid(), possibleSearchOrgUnits ) )
            {
                throw new IllegalQueryException( "Organisation unit is not part of the search scope: " + orgUnit );
            }

            params.getOrganisationUnits().add( organisationUnit );
        }

        validateAssignedUser( criteria );

        if ( criteria.getOuMode() == OrganisationUnitSelectionMode.CAPTURE && user != null )
        {
            params.getOrganisationUnits().addAll( user.getOrganisationUnits() );
        }

        params.setQuery( queryFilter )
            .setProgram( validateProgram( criteria ) )
            .setProgramStatus( criteria.getProgramStatus() )
            .setFollowUp( criteria.getFollowUp() )
            .setLastUpdatedStartDate( criteria.getLastUpdatedStartDate() )
            .setLastUpdatedEndDate( criteria.getLastUpdatedEndDate() )
            .setLastUpdatedDuration( criteria.getLastUpdatedDuration() )
            .setProgramEnrollmentStartDate( programEnrollmentStartDate )
            .setProgramEnrollmentEndDate( programEnrollmentEndDate )
            .setProgramIncidentStartDate( criteria.getProgramIncidentStartDate() )
            .setProgramIncidentEndDate( criteria.getProgramIncidentEndDate() )
            .setTrackedEntityType( validateTrackedEntityType( criteria ) )
            .setOrganisationUnitMode( criteria.getOuMode() )
            .setEventStatus( criteria.getEventStatus() )
            .setEventStartDate( criteria.getEventStartDate() )
            .setEventEndDate( criteria.getEventEndDate() )
            .setAssignedUserSelectionMode( criteria.getAssignedUserMode() )
            .setAssignedUsers( criteria.getAssignedUsers() )
            .setSkipMeta( criteria.isSkipMeta() )
            .setPage( criteria.getPage() )
            .setPageSize( criteria.getPageSize() )
            .setTotalPages( criteria.isTotalPages() )
            .setSkipPaging( PagerUtils.isSkipPaging( criteria.getSkipPaging(), criteria.getPaging() ) )
            .setIncludeDeleted( criteria.isIncludeDeleted() )
            .setIncludeAllAttributes( criteria.isIncludeAllAttributes() )
            .setUser( user )
            .setOrders( getOrderParams( criteria ) );

        return params;

    }

    /**
     * Creates a QueryFilter from the given query string. Query is on format
     * {operator}:{filter-value}. Only the filter-value is mandatory. The EQ
     * QueryOperator is used as operator if not specified.
     */
    private QueryFilter getQueryFilter( String query )
    {
        if ( query == null || query.isEmpty() )
        {
            return null;
        }

        if ( !query.contains( DimensionalObject.DIMENSION_NAME_SEP ) )
        {
            return new QueryFilter( QueryOperator.EQ, query );
        }
        else
        {
            String[] split = query.split( DimensionalObject.DIMENSION_NAME_SEP );

            if ( split.length != 2 )
            {
                throw new IllegalQueryException( "Query has invalid format: " + query );
            }

            QueryOperator op = QueryOperator.fromString( split[0] );

            return new QueryFilter( op, split[1] );
        }
    }

    /**
     * Creates a QueryItem from the given item string. Item is on format
     * {attribute-id}:{operator}:{filter-value}[:{operator}:{filter-value}]. Only
     * the attribute-id is mandatory.
     */
    private QueryItem getQueryItem( String item )
    {
        String[] split = item.split( DimensionalObject.DIMENSION_NAME_SEP );

        if ( split.length % 2 != 1 )
        {
            throw new IllegalQueryException( "Query item or filter is invalid: " + item );
        }

        QueryItem queryItem = getItem( split[0] );

        if ( split.length > 1 ) // Filters specified
        {
            for ( int i = 1; i < split.length; i += 2 )
            {
                QueryOperator operator = QueryOperator.fromString( split[i] );
                queryItem.getFilters().add( new QueryFilter( operator, split[i + 1] ) );
            }
        }

        return queryItem;
    }

    private QueryItem getItem( String item )
    {
        TrackedEntityAttribute at = attributeService.getTrackedEntityAttribute( item );

        if ( at == null )
        {
            throw new IllegalQueryException( "Attribute does not exist: " + item );
        }

        return new QueryItem( at, null, at.getValueType(), at.getAggregationType(), at.getOptionSet(), at.isUnique() );
    }

    private Program validateProgram( TrackedEntityInstanceCriteria criteria )
    {
        Function<String, Program> getProgram = uid -> {
            if ( isNotEmpty( uid ) )
            {
                return programService.getProgram( uid );
            }
            return null;
        };
        
        final Program program = getProgram.apply( criteria.getProgram() );
        if ( isNotEmpty( criteria.getProgram() ) && program == null )
        {
            throw new IllegalQueryException( "Program does not exist: " + criteria.getProgram() );
        }
        return program;
    }

    private TrackedEntityType validateTrackedEntityType( TrackedEntityInstanceCriteria criteria )
    {
        Function<String, TrackedEntityType> getTeiType = uid -> {
            if ( isNotEmpty( uid ) )
            {
                return trackedEntityTypeService.getTrackedEntityType( uid );
            }
            return null;
        };

        final TrackedEntityType trackedEntityType = getTeiType.apply( criteria.getTrackedEntityType() );

        if ( isNotEmpty( criteria.getTrackedEntityType() ) && trackedEntityType == null )
        {
            throw new IllegalQueryException( "Tracked entity type does not exist: " + criteria.getTrackedEntityType() );
        }
        return trackedEntityType;
    }

    private void validateAssignedUser( TrackedEntityInstanceCriteria criteria )
    {
        if ( criteria.getAssignedUserMode() != null && !criteria.getAssignedUsers().isEmpty()
            && !criteria.getAssignedUserMode().equals( AssignedUserSelectionMode.PROVIDED ) )
        {
            throw new IllegalQueryException(
                "Assigned User uid(s) cannot be specified if selectionMode is not PROVIDED" );
        }
    }

    private List<String> getOrderParams( TrackedEntityInstanceCriteria criteria )
    {
        if ( !StringUtils.isEmpty( criteria.getOrder() ) )
        {
            return Arrays.asList( criteria.getOrder().split( "," ) );
        }

        return null;
    }
}
