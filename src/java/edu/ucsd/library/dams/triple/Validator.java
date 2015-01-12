package edu.ucsd.library.dams.triple;

import java.util.HashSet;
import java.util.Set;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.NodeIterator;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.StmtIterator;
import com.hp.hpl.jena.vocabulary.RDF;

import org.apache.log4j.Logger;


/**
 * Utilities to validate objects.
 * @author escowles
 * @since 2015-01-12
**/
public class Validator
{
    private static Logger log = Logger.getLogger( Validator.class );

    public static Set<String> validateModel( Model model, Set<String> validClasses,
        Set<String> validProperties )
    {
        Set<String> errors = new HashSet<>();

        // classes must be present in DAMS/MADS ontologies
        if ( validClasses != null && validClasses.size() > 0 )
        {
            NodeIterator classes = model.listObjectsOfProperty( RDF.type );
            while ( classes.hasNext() )
            {
                RDFNode classNode = classes.next();
                if ( classNode.isURIResource() )
                {
                    String classURI = classNode.asResource().getURI();
                    if ( !validClasses.contains(classURI) )
                    {
                        log.warn( "Invalid class: " + classURI );
                        errors.add( "Invalid class: " + classURI );
                    }
                }
            }
        }

        // predicates must be present in DAMS/MADS/RDF/RDFS ontologies
        if ( validProperties != null && validProperties.size() > 0 )
        {
            StmtIterator statements = model.listStatements();
            while ( statements.hasNext() )
            {
                String propURI = statements.nextStatement().getPredicate().getURI();
                if ( !validProperties.contains(propURI) )
                {
                    log.warn( "Invalid property: " + propURI );
                    errors.add( "Invalid property: " + propURI );
                }
            }
        }

        return errors;
    }

}
