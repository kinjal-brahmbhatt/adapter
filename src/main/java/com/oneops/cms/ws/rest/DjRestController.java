/*******************************************************************************
 *  
 *   Copyright 2015 Walmart, Inc.
 *  
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *  
 *       http://www.apache.org/licenses/LICENSE-2.0
 *  
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *  
 *******************************************************************************/
package com.oneops.cms.ws.rest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.oneops.cms.dj.domain.CmsRelease;
import com.oneops.cms.dj.domain.CmsRfcCI;
import com.oneops.cms.dj.domain.CmsRfcRelation;
import com.oneops.cms.dj.service.CmsDjManager;
import com.oneops.cms.exceptions.CIValidationException;
import com.oneops.cms.exceptions.DJException;
import com.oneops.cms.simple.domain.CmsRfcCISimple;
import com.oneops.cms.simple.domain.CmsRfcRelationSimple;
import com.oneops.cms.util.CmsError;
import com.oneops.cms.util.CmsUtil;
import com.oneops.cms.ws.exceptions.CmsSecurityException;
import com.oneops.cms.ws.rest.util.CmsScopeVerifier;

@Controller
public class DjRestController extends AbstractRestController {
	
	private CmsDjManager djManager;
	private CmsUtil cmsUtil;
	private CmsScopeVerifier scopeVerifier; 
	
	@Autowired
    public void setCmsUtil(CmsUtil cmsUtil) {
		this.cmsUtil = cmsUtil;
	}
	
	public void setScopeVerifier(CmsScopeVerifier scopeVerifier) {
		this.scopeVerifier = scopeVerifier;
	}

	public void setDjManager(CmsDjManager djManager) {
		this.djManager = djManager;
	}

	@ExceptionHandler(DJException.class)
	public void handleDJExceptions(DJException e, HttpServletResponse response) throws IOException {
		sendError(response,HttpServletResponse.SC_BAD_REQUEST,e);
	}
	
	@ExceptionHandler(CIValidationException.class)
	public void handleCIValidationExceptions(CIValidationException e, HttpServletResponse response) throws IOException {
		sendError(response,HttpServletResponse.SC_BAD_REQUEST,e);
	}
	
	@ExceptionHandler(CmsSecurityException.class)
	public void handleCmsSecurityException(CmsSecurityException e, HttpServletResponse response) throws IOException {
		sendError(response,HttpServletResponse.SC_FORBIDDEN,e);
	}
	
	
	@RequestMapping(value="/dj/simple/releases/{releaseId}", method = RequestMethod.GET)
	@ResponseBody
	public CmsRelease getReleaseById(
			@PathVariable long releaseId,
			@RequestHeader(value="X-Cms-Scope", required = false)  String scope){
		
		CmsRelease release = djManager.getReleaseById(releaseId);
		
		if (release == null) throw new DJException(CmsError.DJ_NO_RELEASE_WITH_GIVEN_ID_ERROR,
                                    "There is no release with this id");
		scopeVerifier.verifyScope(scope, release);
		
		return release;
	}

	@RequestMapping(value="/dj/simple/releases/{releaseId}/commit", method = RequestMethod.GET)
	@ResponseBody
	public CmsRelease commitRelease(@PathVariable long releaseId,
			@RequestParam(value="setDfValue", required = false) Boolean setDfValue, 
			@RequestParam(value="newCiState", required = false) String newCiState,
			@RequestParam(value="desc", required = false) String desc,
			@RequestHeader(value="X-Cms-Scope", required = false)  String scope,
			@RequestHeader(value="X-Cms-User", required = false)  String userId){

		if (setDfValue == null) {
			setDfValue = true;
		}
		
		if (scope != null) {
			CmsRelease release = djManager.getReleaseById(releaseId);
			if (release == null) throw new DJException(CmsError.DJ_NO_RELEASE_WITH_GIVEN_ID_ERROR,
                                                "There is no release with this id");
			scopeVerifier.verifyScope(scope, release);
		}
		
		djManager.commitRelease(releaseId, setDfValue, newCiState, userId, desc);
		return djManager.getReleaseById(releaseId);
	}

	
	
	@RequestMapping(value="/dj/simple/releases", method = RequestMethod.GET)
	@ResponseBody
	public List<CmsRelease> getReleaseBy3(
			@RequestParam("nsPath") String nsPath,  
			@RequestParam(value="releaseName", required = false) String releaseName, 
			@RequestParam(value="releaseState", required = false) String releaseState,
			@RequestParam(value="latest", required = false) Boolean latest,
			@RequestHeader(value="X-Cms-Scope", required = false)  String scope){
		
		List<CmsRelease> relList = null;
		
		if (latest != null && latest.booleanValue()) {
			relList =  djManager.getLatestRelease(nsPath, releaseState);
		} else {
			relList = djManager.getReleaseBy3(nsPath, releaseName, releaseState);
		}
		
		if (scope != null) {
			for (CmsRelease rel : relList) {
				scopeVerifier.verifyScope(scope, rel);
			}
		}	
		
		return relList;
	}
	
	@RequestMapping(method=RequestMethod.POST, value="/dj/simple/releases")
	@ResponseBody
	public CmsRelease createRelease(
			@RequestBody CmsRelease release,
			@RequestHeader(value="X-Cms-Scope", required = false)  String scope, 
			@RequestHeader(value="X-Cms-User", required = false)  String userId) throws DJException {
		
		scopeVerifier.verifyScope(scope, release);
		release.setCreatedBy(userId);
		return djManager.createRelease(release);
	}
	
	@RequestMapping(method=RequestMethod.PUT, value="/dj/simple/releases/{releaseId}")
	@ResponseBody
	public CmsRelease updateRelease(
			@PathVariable long releaseId, 
			@RequestBody CmsRelease release,
			@RequestHeader(value="X-Cms-Scope", required = false)  String scope,
			@RequestHeader(value="X-Cms-User", required = false)  String userId) throws DJException {
		
		release.setReleaseId(releaseId);
		scopeVerifier.verifyScope(scope, release);
		release.setCommitedBy(userId);
		return djManager.updateRelease(release);
	}
	
	@RequestMapping(value="/dj/simple/releases/{releaseId}", method = RequestMethod.DELETE)
	@ResponseBody
	public String deleteRelease(
			@PathVariable long releaseId,
			@RequestHeader(value="X-Cms-Scope", required = false)  String scope){
		
		if (scope != null) {
			CmsRelease release = djManager.getReleaseById(releaseId);
			if (release == null) throw new DJException(CmsError.DJ_NO_RELEASE_WITH_GIVEN_ID_ERROR,
                                            "There is no release with this id");
			scopeVerifier.verifyScope(scope, release);
		}
	
		long deleted =  djManager.deleteRelease(releaseId);
		return "{\"deleted\":" + deleted + "}";

	}
	
	//DJ CIs

	@RequestMapping(value="/dj/simple/rfc/cis/{rfcId}", method = RequestMethod.GET)
	@ResponseBody
	public CmsRfcCISimple getRfcById(
			@PathVariable long rfcId,
			@RequestHeader(value="X-Cms-Scope", required = false)  String scope){

		CmsRfcCI rfc = djManager.getRfcCIById(rfcId);
		if (rfc == null) throw new DJException(CmsError.DJ_NO_RFC_WITH_GIVEN_ID_ERROR,
        										"There is no rfc with this id");
		scopeVerifier.verifyScope(scope, rfc);
		
		return cmsUtil.custRfcCI2RfcCISimple(rfc);
	}
	
	
	@RequestMapping(value="/dj/rfc/cis/{rfcId}", method = RequestMethod.GET)
	@ResponseBody
	public CmsRfcCI getRfcByIdFull(
			@PathVariable long rfcId,
			@RequestHeader(value="X-Cms-Scope", required = false)  String scope){

		CmsRfcCI rfc = djManager.getRfcCIById(rfcId);
		if (rfc == null) throw new DJException(CmsError.DJ_NO_RFC_WITH_GIVEN_ID_ERROR,
                                                "There is no rfc with this id");

		scopeVerifier.verifyScope(scope, rfc);
	
		return rfc;
	}
	
	
	@RequestMapping(value="/dj/simple/rfc/cis", method = RequestMethod.GET)
	@ResponseBody
	public List<CmsRfcCISimple> getRfcCiBy3(
			@RequestParam("releaseId") long releaseId,  
			@RequestParam(value="isActive", required = false) Boolean isActive, 
			@RequestParam(value="ciId", required = false) Long ciId,
			@RequestHeader(value="X-Cms-Scope", required = false)  String scope){
		
		if (isActive == null) {
			isActive = true;
		}
		List<CmsRfcCI> rfcList = djManager.getRfcCIBy3(releaseId, isActive, ciId);
		List<CmsRfcCISimple> rfcSimpleList = new ArrayList<CmsRfcCISimple>();
		for (CmsRfcCI rfc : rfcList) {
			scopeVerifier.verifyScope(scope, rfc);
			rfcSimpleList.add(cmsUtil.custRfcCI2RfcCISimple(rfc));
		}
		return rfcSimpleList;
	}
	
	
	@RequestMapping(method=RequestMethod.POST, value="/dj/simple/rfc/cis")
	@ResponseBody
	public CmsRfcCISimple createRfcCi(
			@RequestBody CmsRfcCISimple rfcSimple,
			@RequestHeader(value="X-Cms-Scope", required = false)  String scope,
			@RequestHeader(value="X-Cms-User", required = false)  String userId) throws DJException {

		scopeVerifier.verifyScope(scope, rfcSimple);
		CmsRfcCI rfc = cmsUtil.custRfcCISimple2RfcCI(rfcSimple);
		rfc.setCreatedBy(userId);
		return cmsUtil.custRfcCI2RfcCISimple(djManager.createRfcCI(rfc));
	}
	
	@RequestMapping(method=RequestMethod.PUT, value="/dj/simple/rfc/cis/{rfcId}")
	@ResponseBody
	public CmsRfcCISimple updateRfcCi(
			@PathVariable long rfcId, 
			@RequestBody CmsRfcCISimple rfcSimple,
			@RequestHeader(value="X-Cms-Scope", required = false)  String scope,
			@RequestHeader(value="X-Cms-User", required = false)  String userId) throws DJException {
		
		scopeVerifier.verifyScope(scope, rfcSimple);

		rfcSimple.setRfcId(rfcId);
		CmsRfcCI rfc = cmsUtil.custRfcCISimple2RfcCI(rfcSimple);
		rfc.setUpdatedBy(userId);
		return cmsUtil.custRfcCI2RfcCISimple(djManager.updateRfcCI(rfc));
	}
	
	@RequestMapping(value="/dj/simple/rfc/cis/{rfcId}", method = RequestMethod.DELETE)
	@ResponseBody
	public String rmRfcCiFromRelease(
			@PathVariable long rfcId,
			@RequestHeader(value="X-Cms-Scope", required = false)  String scope){
		
		if (scope != null) {
			CmsRfcCI rfc = djManager.getRfcCIById(rfcId);
			if (rfc == null) throw new DJException(CmsError.DJ_NO_RELEASE_WITH_GIVEN_ID_ERROR,
                                                    "There is no release with this id");
			scopeVerifier.verifyScope(scope, rfc);
		}

		long deleted =  djManager.rmRfcCiFromRelease(rfcId);
		return "{\"deleted\":" + deleted + "}";
	}

	// DJ Relations
	
	@RequestMapping(value="/dj/simple/rfc/relations/{rfcId}", method = RequestMethod.GET)
	@ResponseBody
	public CmsRfcRelationSimple getRfcRelationById(
			@PathVariable long rfcId,
			@RequestHeader(value="X-Cms-Scope", required = false)  String scope){
		
		CmsRfcRelation rfc = djManager.getRfcRelationById(rfcId);
		
		if (rfc == null) throw new DJException(CmsError.DJ_NO_RFC_WITH_GIVEN_ID_ERROR,
                                                "There is no rfc relation with this id");

		scopeVerifier.verifyScope(scope, rfc);
		
		return cmsUtil.custRfcRel2RfcRelSimple(rfc);
	}

	@RequestMapping(value="/dj/rfc/relations/{rfcId}", method = RequestMethod.GET)
	@ResponseBody
	public CmsRfcRelation getRfcRelationByIdFull(
			@PathVariable long rfcId,
			@RequestHeader(value="X-Cms-Scope", required = false)  String scope){
		
		CmsRfcRelation rfc = djManager.getRfcRelationById(rfcId);
		
		if (rfc == null) throw new DJException(CmsError.DJ_NO_RFC_WITH_GIVEN_ID_ERROR,
                                                        "There is no rfc relation with this id");

		scopeVerifier.verifyScope(scope, rfc);
		
		return rfc;
	}
	
	
	@RequestMapping(value="/dj/simple/rfc/relations", method = RequestMethod.GET)
	@ResponseBody
	public List<CmsRfcRelationSimple> getRfcRelationBy3(
			@RequestParam("releaseId") long releaseId,  
			@RequestParam(value="isActive", required = false) Boolean isActive, 
			@RequestParam(value="fromCiId", required = false) Long fromCiId,
			@RequestParam(value="toCiId", required = false) Long toCiId,
			@RequestHeader(value="X-Cms-Scope", required = false)  String scope){
		
		if (isActive == null) {
			isActive = true;
		}
		List<CmsRfcRelation> relList = djManager.getRfcRelationBy3(releaseId, isActive, fromCiId, toCiId);
		List<CmsRfcRelationSimple> relSimpleList = new ArrayList<CmsRfcRelationSimple>();
		for (CmsRfcRelation rel : relList) {

			scopeVerifier.verifyScope(scope, rel);

			relSimpleList.add(cmsUtil.custRfcRel2RfcRelSimple(rel));
		}
		return relSimpleList;
	}
	
	@RequestMapping(method=RequestMethod.POST, value="/dj/simple/rfc/relations")
	@ResponseBody
	public CmsRfcRelationSimple createRfcRelation(
			@RequestBody CmsRfcRelationSimple relSimple,
			@RequestHeader(value="X-Cms-Scope", required = false)  String scope,
			@RequestHeader(value="X-Cms-User", required = false)  String userId) throws DJException {

		scopeVerifier.verifyScope(scope, relSimple);

		CmsRfcRelation rel = cmsUtil.custRfcRelSimple2RfcRel(relSimple);
		rel.setCreatedBy(userId);
		return cmsUtil.custRfcRel2RfcRelSimple(djManager.createRfcRelation(rel));
	}
	
	@RequestMapping(method=RequestMethod.PUT, value="/dj/simple/rfc/relations/{rfcId}")
	@ResponseBody
	public CmsRfcRelationSimple updateRfcRelation(
			@PathVariable long rfcId, 
			@RequestBody CmsRfcRelationSimple relSimple,
			@RequestHeader(value="X-Cms-Scope", required = false)  String scope,
			@RequestHeader(value="X-Cms-User", required = false)  String userId) throws DJException {
		
		scopeVerifier.verifyScope(scope, relSimple);

		relSimple.setRfcId(rfcId);
		CmsRfcRelation rel = cmsUtil.custRfcRelSimple2RfcRel(relSimple);
		rel.setUpdatedBy(userId);
		return cmsUtil.custRfcRel2RfcRelSimple(djManager.updateRfcRelation(rel));
	}
	
	@RequestMapping(value="/dj/simple/rfc/relations/{rfcId}", method = RequestMethod.DELETE)
	@ResponseBody
	public String rmRfcRelFromRelease(
			@PathVariable long rfcId,
			@RequestHeader(value="X-Cms-Scope", required = false)  String scope){
	
		if (scope != null) {
			CmsRfcRelation rfc = djManager.getRfcRelationById(rfcId);
			if (rfc == null) throw new DJException(CmsError.DJ_NO_RELEASE_WITH_GIVEN_ID_ERROR,
                                                            "There is no release with this id");
			scopeVerifier.verifyScope(scope, rfc);
		}
		
		long deleted =  djManager.rmRfcRelationFromRelease(rfcId);
		return "{\"deleted\":" + deleted + "}";
	}

	
	
}
