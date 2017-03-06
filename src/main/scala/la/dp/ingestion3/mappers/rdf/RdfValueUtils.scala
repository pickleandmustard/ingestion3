package la.dp.ingestion3.mappers.rdf

import org.eclipse.rdf4j.model._
import org.eclipse.rdf4j.model.impl.SimpleValueFactory

trait RdfValueUtils {

    import RdfValueUtils._

    def literal(string: String): Literal =
      valueFactory.createLiteral(string)

    def iri(string: String): IRI =
      valueFactory.createIRI(string)

    def iri(namespace: String, localName: String): IRI =
      valueFactory.createIRI(namespace, localName)

    def bnode(): BNode =
      valueFactory.createBNode()

    def bnode(nodeId: String): BNode =
      valueFactory.createBNode(nodeId)

    def stmt(subj: Resource, pred: IRI, obj: Value): Statement =
      valueFactory.createStatement(subj, pred, obj)

}

object RdfValueUtils {
  private lazy val valueFactory = SimpleValueFactory.getInstance()
}

