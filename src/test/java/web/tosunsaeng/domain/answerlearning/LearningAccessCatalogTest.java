package web.tosunsaeng.domain.answerlearning;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static web.tosunsaeng.domain.answerlearning.LearningFixtures.*;

class LearningAccessCatalogTest {
    MongoTemplate mongo=mock(MongoTemplate.class);
    LearningStorage storage=mock(LearningStorage.class);
    LearningPaidAccess access=mock(LearningPaidAccess.class);
    LearningProperties config=config();
    LearningCatalog catalog;
    Document unit(String id,int part) {
        return new Document("_id",id).append("part",part).append("publicationStatus","ready")
                .append("lessonIds",List.of());
    }
    LearningAccessCatalogTest() {
        config.setPaidAccessEnabled(true);
        config.setFreeUnits(Map.of(1,"free1",2,"free2",3,"free3",4,"free4",5,"free5"));
        catalog=new LearningCatalog(mongo,config,storage,access);
        when(mongo.findOne(any(Query.class),eq(Document.class),eq("learning_datasets")))
                .thenReturn(new Document("status","ready"));
        when(mongo.findOne(any(Query.class),eq(Document.class),eq("learning_units"))).thenAnswer(i -> {
            String id=((Query)i.getArgument(0)).getQueryObject().getString("_id");
            return unit(id,id.startsWith("free")?Integer.parseInt(id.substring(4)):2);
        });
        when(access.prepare(OWNER)).thenReturn(() -> {});
    }
    @Test void allFiveFreeUnitsAndListsNeverCallBillingButPaidDetailsDo() {
        catalog.validateDataset();
        for (int part=1;part<=5;part++) for (String level:List.of("IM3","IH","AL"))
            catalog.detail(OWNER,"free"+part,level);
        catalog.list(OWNER,2,"IH");
        verifyNoInteractions(access);
        Runnable fresh=mock(Runnable.class); when(access.prepare(OWNER)).thenReturn(fresh);
        catalog.detail(OWNER,"paid","IH");
        verify(access).prepare(OWNER); verify(fresh).run();
    }
    @Test void paidDenialStopsBeforeMediaAndNoOwnerlessMediaBypass() {
        when(access.prepare(OWNER)).thenThrow(new LearningFailure(402,"ENTITLEMENT_INSUFFICIENT"));
        assertThatThrownBy(() -> catalog.detail(OWNER,"paid","IH")).hasMessage("ENTITLEMENT_INSUFFICIENT");
        assertThatThrownBy(() -> catalog.media("audio")).hasMessage("LEARNING_NOT_FOUND");
        verifyNoInteractions(storage);
    }
    @Test void mediaRefreshRequiresACurrentContentReference() {
        assertThatThrownBy(() -> catalog.media(OWNER,"orphan")).hasMessage("LEARNING_NOT_FOUND");
        verifyNoInteractions(access,storage);
        when(mongo.find(any(Query.class),eq(Document.class),eq("learning_lessons")))
                .thenReturn(List.of(new Document("unitId","paid")));
        when(mongo.find(any(Query.class),eq(Document.class),eq("learning_media")))
                .thenReturn(List.of(new Document("_id","photo")));
        when(storage.media(any())).thenReturn(new Document("id","photo"));
        catalog.media(OWNER,"photo"); verify(access).prepare(OWNER);
    }
    @Test void reuseScopesQueriesAndRefreshesManifestWithoutCaching() {
        config.setPaidAccessEnabled(false);
        var source=new Document("datasetId","old").append("contentVersion","v1").append("bucket",config.getBucket())
                .append("prefix","answer-learning/old/v1");
        var manifest=new Document("status","ready").append("mediaPolicy","reuse-published-media").append("mediaSource",source);
        when(mongo.findOne(any(Query.class),eq(Document.class),eq("learning_datasets"))).thenReturn(manifest);
        when(mongo.exists(any(Query.class),eq("learning_datasets"))).thenReturn(true);
        when(mongo.find(any(Query.class),eq(Document.class),eq("learning_media"))).thenReturn(List.of(new Document("_id","audio")));
        when(storage.media(any(),any())).thenReturn(new Document("id","audio"));
        catalog.media(OWNER,"audio");
        var query=org.mockito.ArgumentCaptor.forClass(Query.class);
        verify(mongo).find(query.capture(),eq(Document.class),eq("learning_media"));
        assertThat(query.getValue().getQueryObject()).containsEntry("datasetId","old").containsEntry("contentVersion","v1")
                .containsEntry("accessScope","authenticated-learning");
        source.put("bucket","other");
        assertThatThrownBy(() -> catalog.media(OWNER,"audio")).hasMessage("LEARNING_UNAVAILABLE");
    }
    @Test void missingSourceManifestFailsClosed() {
        when(mongo.findOne(any(Query.class),eq(Document.class),eq("learning_datasets"))).thenReturn(new Document("status","ready")
                .append("mediaPolicy","reuse-published-media").append("mediaSource",new Document("datasetId","old")
                        .append("contentVersion","v1").append("bucket",config.getBucket()).append("prefix","answer-learning/old/v1")));
        assertThatThrownBy(catalog::validateDataset).hasMessage("LEARNING_UNAVAILABLE");
    }
}
