package com.zimo.regtech.ontology.mapper;

import com.zimo.regtech.ontology.entity.OntologyClass;
import com.zimo.regtech.ontology.entity.OntologyIndividual;
import com.zimo.regtech.ontology.entity.OntologyProperty;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface OntologyMapper {

    List<OntologyClass> selectClasses(@Param("keyword") String keyword);

    OntologyClass selectClass(@Param("id") Long id);

    OntologyClass selectClassByCode(@Param("code") String code);

    int insertClass(OntologyClass clazz);

    int updateClass(OntologyClass clazz);

    int deleteClass(@Param("id") Long id);

    long countClasses();

    long countChildren(@Param("id") Long id);

    List<OntologyProperty> selectProperties(@Param("classId") Long classId);

    int insertProperty(OntologyProperty property);

    int deleteProperty(@Param("id") Long id);

    long countProperties();

    List<OntologyIndividual> selectIndividuals(@Param("classId") Long classId,
                                               @Param("keyword") String keyword);

    OntologyIndividual selectIndividual(@Param("id") Long id);

    int insertIndividual(OntologyIndividual individual);

    int updateIndividual(OntologyIndividual individual);

    int deleteIndividual(@Param("id") Long id);

    long countIndividuals();
}
