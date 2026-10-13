package web.tosunsaeng.domain.answerlearning;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static web.tosunsaeng.domain.answerlearning.LearningFixtures.*;

/** Optional offline check of the private import pack; no content is checked into this repository. */
@EnabledIfEnvironmentVariable(named="LEARNING_IMPORT_FIXTURES",matches=".+")
class LearningImportedContentTest {
    @Test void entireImportedBankProjectsIntoTheFrontendContractWithoutExternalCalls() throws Exception {
        Path root=Path.of(System.getenv("LEARNING_IMPORT_FIXTURES"));
        var json=new ObjectMapper(); Map<String,List<Document>> db=new HashMap<>();
        for (String name : List.of("learning_units","learning_lessons","learning_answers","learning_media","learning_checklists","learning_datasets")) {
            List<Document> rows=new ArrayList<>(); for (var row : json.readTree(Files.readString(root.resolve(name+".json")))) rows.add(Document.parse(row.toString())); db.put(name,rows);
        }
        var dataset=db.get("learning_datasets").getFirst(); var config=config(); config.setDatasetId(dataset.getString("datasetId")); config.setContentVersion(dataset.getString("contentVersion"));
        var mongo=mock(MongoTemplate.class); var storage=mock(LearningStorage.class);
        when(mongo.find(any(Query.class),eq(Document.class),anyString())).thenAnswer(i -> select(db,i.getArgument(2),i.getArgument(0)));
        when(mongo.findOne(any(Query.class),eq(Document.class),anyString())).thenAnswer(i -> select(db,i.getArgument(2),i.getArgument(0)).stream().findFirst().orElse(null));
        when(storage.media(any())).thenAnswer(i->{ Document row=i.getArgument(0); var result=new Document("id",row.getString("_id"))
                .append("url","https://media.example.test/"+row.getString("_id")).append("expiresAt","2099-01-01T00:00:00Z");
            if(row.containsKey("width")) { result.put("width",row.get("width")); result.put("height",row.get("height")); } return result; });
        var catalog=new LearningCatalog(mongo,config,storage); List<Document> details=new ArrayList<>(), catalogs=new ArrayList<>();
        for (int part=1; part<=5; part++) for (String level : part==1?List.of("common"):List.of("IM3","IH","AL")) {
            Document list=catalog.list(OWNER,part,level); catalogs.add(list);
            assertThat(list.getList("units",Document.class)).hasSize(40);
            for (Document u : list.getList("units",Document.class)) details.add(catalog.detail(OWNER,u.getString("id"),level));
        }
        assertThat(details.stream().mapToInt(d->d.getList("lessons",Document.class).size()).sum()).isEqualTo(1000);
        Files.createDirectories(Path.of("build"));
        Files.writeString(Path.of("build/answer-learning-contract.json"),json.writeValueAsString(Map.of("catalogs",catalogs,"details",details)));
    }
    private static List<Document> select(Map<String,List<Document>> db,String collection,Query query) {
        return db.getOrDefault(collection,List.of()).stream().filter(d->query.getQueryObject().entrySet().stream().allMatch(e -> {
            Object v=e.getValue(); if (v instanceof Document operator && operator.containsKey("$in")) return ((Collection<?>)operator.get("$in")).contains(d.get(e.getKey()));
            return Objects.equals(v,d.get(e.getKey()));
        })).toList();
    }
}
