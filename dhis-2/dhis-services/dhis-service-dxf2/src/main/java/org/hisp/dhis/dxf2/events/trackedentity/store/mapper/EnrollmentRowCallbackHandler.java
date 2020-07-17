/*
 * Copyright (c) 2004-2019, University of Oslo
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * Neither the name of the HISP project nor the names of its contributors may
 * be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.hisp.dhis.dxf2.events.trackedentity.store.mapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.io.WKTReader;
import org.hisp.dhis.dxf2.events.enrollment.Enrollment;
import org.hisp.dhis.dxf2.events.enrollment.EnrollmentStatus;
import org.hisp.dhis.dxf2.events.trackedentity.store.TableColumn;
import org.hisp.dhis.dxf2.events.trackedentity.store.query.EnrollmentQuery;
import org.hisp.dhis.organisationunit.FeatureType;
import org.hisp.dhis.util.DateUtils;
import org.postgresql.util.PGobject;

import static org.hisp.dhis.dxf2.events.trackedentity.store.query.EnrollmentQuery.COLUMNS.COMPLETED;
import static org.hisp.dhis.dxf2.events.trackedentity.store.query.EnrollmentQuery.COLUMNS.COMPLETEDBY;
import static org.hisp.dhis.dxf2.events.trackedentity.store.query.EnrollmentQuery.COLUMNS.CREATED;
import static org.hisp.dhis.dxf2.events.trackedentity.store.query.EnrollmentQuery.COLUMNS.CREATEDCLIENT;
import static org.hisp.dhis.dxf2.events.trackedentity.store.query.EnrollmentQuery.COLUMNS.DELETED;
import static org.hisp.dhis.dxf2.events.trackedentity.store.query.EnrollmentQuery.COLUMNS.ENROLLMENTDATE;
import static org.hisp.dhis.dxf2.events.trackedentity.store.query.EnrollmentQuery.COLUMNS.FOLLOWUP;
import static org.hisp.dhis.dxf2.events.trackedentity.store.query.EnrollmentQuery.COLUMNS.GEOMETRY;
import static org.hisp.dhis.dxf2.events.trackedentity.store.query.EnrollmentQuery.COLUMNS.ID;
import static org.hisp.dhis.dxf2.events.trackedentity.store.query.EnrollmentQuery.COLUMNS.INCIDENTDATE;
import static org.hisp.dhis.dxf2.events.trackedentity.store.query.EnrollmentQuery.COLUMNS.ORGUNIT_NAME;
import static org.hisp.dhis.dxf2.events.trackedentity.store.query.EnrollmentQuery.COLUMNS.ORGUNIT_UID;
import static org.hisp.dhis.dxf2.events.trackedentity.store.query.EnrollmentQuery.COLUMNS.PROGRAM_UID;
import static org.hisp.dhis.dxf2.events.trackedentity.store.query.EnrollmentQuery.COLUMNS.STATUS;
import static org.hisp.dhis.dxf2.events.trackedentity.store.query.EnrollmentQuery.COLUMNS.STOREDBY;
import static org.hisp.dhis.dxf2.events.trackedentity.store.query.EnrollmentQuery.COLUMNS.TEI_TYPE_UID;
import static org.hisp.dhis.dxf2.events.trackedentity.store.query.EnrollmentQuery.COLUMNS.TEI_UID;
import static org.hisp.dhis.dxf2.events.trackedentity.store.query.EnrollmentQuery.COLUMNS.UID;
import static org.hisp.dhis.dxf2.events.trackedentity.store.query.EnrollmentQuery.COLUMNS.UPDATED;
import static org.hisp.dhis.dxf2.events.trackedentity.store.query.EnrollmentQuery.COLUMNS.UPDATEDCLIENT;
import static org.hisp.dhis.dxf2.events.trackedentity.store.query.EnrollmentQuery.getColumnName;

/**
 * @author Luciano Fiandesio
 */
public class EnrollmentRowCallbackHandler extends AbstractMapper<Enrollment>
{
    @Override
    Enrollment getItem( ResultSet rs )
        throws SQLException
    {
        return getEnrollment( rs );
    }

    @Override
    String getKeyColumn()
    {
        return "tei_uid";
    }

    private Enrollment getEnrollment( ResultSet rs )
        throws SQLException
    {
        Enrollment enrollment = new Enrollment();
        enrollment.setEnrollment( rs.getString( getColumnName( UID ) ) );

        Optional<Geometry> geo = MapperGeoUtils.resolveGeometry( rs.getBytes( getColumnName( GEOMETRY ) ) );
        if ( geo.isPresent() )
        {
            enrollment.setGeometry( geo.get() );
            if ( rs.getString( "program_feature_type" ).equalsIgnoreCase( FeatureType.POINT.value() ) )
            {
                com.vividsolutions.jts.geom.Coordinate co = enrollment.getGeometry().getCoordinate();
                enrollment.setCoordinate( new org.hisp.dhis.dxf2.events.event.Coordinate( co.x, co.y ) );
            }
        }
        enrollment.setTrackedEntityType( rs.getString( getColumnName( TEI_TYPE_UID ) ) );
        enrollment.setTrackedEntityInstance( rs.getString( getColumnName( TEI_UID ) ) );
        enrollment.setOrgUnit( rs.getString( getColumnName( ORGUNIT_UID ) ) );
        enrollment.setOrgUnitName( rs.getString( getColumnName( ORGUNIT_NAME ) ) );
        enrollment.setCreated( DateUtils.getIso8601NoTz( rs.getTimestamp( getColumnName( CREATED ) ) ) );
        enrollment.setCreatedAtClient( DateUtils.getIso8601NoTz( rs.getTimestamp( getColumnName( CREATEDCLIENT ) ) ) );
        enrollment.setLastUpdated( DateUtils.getIso8601NoTz( rs.getTimestamp( getColumnName( UPDATED ) ) ) );
        enrollment
            .setLastUpdatedAtClient( DateUtils.getIso8601NoTz( rs.getTimestamp( getColumnName( UPDATEDCLIENT ) ) ) );
        enrollment.setProgram( rs.getString( getColumnName( PROGRAM_UID ) ) );
        enrollment.setStatus( EnrollmentStatus.fromStatusString( rs.getString( getColumnName( STATUS ) ) ) );
        enrollment.setEnrollmentDate( rs.getTimestamp( getColumnName( ENROLLMENTDATE ) ) );
        enrollment.setIncidentDate( rs.getTimestamp( getColumnName( INCIDENTDATE ) ) );
        enrollment.setFollowup( rs.getBoolean( getColumnName( FOLLOWUP ) ) );
        enrollment.setCompletedDate( rs.getTimestamp( getColumnName( COMPLETED ) ) );
        enrollment.setCompletedBy( rs.getString( getColumnName( COMPLETEDBY ) ) );
        enrollment.setStoredBy( rs.getString( getColumnName( STOREDBY ) ) );
        enrollment.setDeleted( rs.getBoolean( getColumnName( DELETED ) ) );

        return enrollment;
    }
}
