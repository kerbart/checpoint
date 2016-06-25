package com.kerbart.checkpoint.services;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Date;
import java.util.List;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import javax.transaction.Transactional;

import org.springframework.stereotype.Repository;

import com.kerbart.checkpoint.exceptions.ApplicationDoesNotExistException;
import com.kerbart.checkpoint.model.Application;
import com.kerbart.checkpoint.model.Ordonnance;
import com.kerbart.checkpoint.model.Patient;
import com.kerbart.checkpoint.model.PatientDansTournee;
import com.kerbart.checkpoint.model.SecuredFile;
import com.kerbart.checkpoint.repositories.ApplicationRepository;
import com.kerbart.checkpoint.repositories.OrdonnanceRepository;

@Repository("patientService")
@Transactional
public class PatientService {

    @PersistenceContext
    private EntityManager em;

    @Inject
    ApplicationRepository applicationRepository;

    @Inject
    OrdonnanceRepository ordonnanceRepository;

    @Inject
    FileService fileService;

    public Patient createPatient(Patient patient, String applicationToken) throws ApplicationDoesNotExistException {
        Application app = applicationRepository.findByToken(applicationToken);
        if (app == null) {
            throw new ApplicationDoesNotExistException();
        }
        patient.setApplication(app);
        em.persist(patient);
        return patient;
    }

    public Ordonnance createOrdonance(Patient patient, String applicationToken, Date dateDebut, Date dateFin)
            throws ApplicationDoesNotExistException {
        Application app = applicationRepository.findByToken(applicationToken);
        if (app == null) {
            throw new ApplicationDoesNotExistException();
        }
        Ordonnance ordonnance = new Ordonnance(patient);
        ordonnance.setDateDebut(dateDebut);
        ordonnance.setDateFin(dateFin);
        em.persist(ordonnance);
        return ordonnance;
    }

    public SecuredFile addFileOrdonance(String applicationToken, String ordonnanceToken, String contentType,
            byte[] bytes) throws ApplicationDoesNotExistException {
        Application app = applicationRepository.findByToken(applicationToken);
        if (app == null) {
            throw new ApplicationDoesNotExistException();
        }
        Ordonnance ordonnance = ordonnanceRepository.findByToken(ordonnanceToken);
        SecuredFile securedFile = new SecuredFile();
        securedFile.setOrdonnance(ordonnance);
        securedFile.setDateCreation(new Date());
        securedFile.setContentType(contentType);
        String path = fileService.storeFile(app.getToken(), fileService.convertToFile(bytes));
        securedFile.setPath(path);
        em.persist(securedFile);

        return securedFile;
    }

    public byte[] getFileOrdonnance(String applicationToken, String fileToken)
            throws ApplicationDoesNotExistException {
        Application app = applicationRepository.findByToken(applicationToken);
        if (app == null) {
            throw new ApplicationDoesNotExistException();
        }
        Query query = em.createQuery("select s from SecuredFile s " + " where s.token = :fileToken ")
                .setParameter("fileToken", fileToken);

        SecuredFile sec = (SecuredFile) query.getSingleResult();
        File decryptedFile = fileService.decryptFile(applicationToken, new File(sec.getPath()));
        try {
            byte[] content = Files.readAllBytes(Paths.get(decryptedFile.getAbsolutePath()));
            return content;
        } catch (IOException e) {
            return null;
        }
    }
    
    public List<Ordonnance> getOrdonnances(String applicationToken, String patientToken) throws ApplicationDoesNotExistException {
    	 Application app = applicationRepository.findByToken(applicationToken);
         if (app == null) {
             throw new ApplicationDoesNotExistException();
         }
    	return ordonnanceRepository.findByPatientToken(patientToken);
    }

    public Patient updatePatient(Patient patient, String applicationToken) throws ApplicationDoesNotExistException {
        Application app = applicationRepository.findByToken(applicationToken);
        if (app == null) {
            throw new ApplicationDoesNotExistException();
        }
        patient.setApplication(app);
        return em.merge(patient);
    }

    public PatientDansTournee findPatientDansTourneeOccurence(String patientToken, String tourneeOccurenceToken) {
        Query query = em
                .createQuery("select pdt from PatientDansTournee pdt "
                        + " where pdt.tourneeOccurence.token = :tourneeOccurenceToken "
                        + " and pdt.patient.token = :patientToken")
                .setParameter("tourneeOccurenceToken", tourneeOccurenceToken)
                .setParameter("patientToken", patientToken);
        return (PatientDansTournee) query.getSingleResult();
    }

    public void removePatientDansTourneeOccurence(String patientToken, String tourneeOccurenceToken) {
        PatientDansTournee pdt = this.findPatientDansTourneeOccurence(patientToken, tourneeOccurenceToken);
        if (pdt != null) {
            em.remove(pdt);
        }
    }

}
