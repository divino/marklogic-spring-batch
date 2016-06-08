package com.marklogic.spring.batch.job;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.marklogic.client.helper.DatabaseClientProvider;
import com.marklogic.spring.batch.item.DocumentItemWriter;
import com.marklogic.spring.batch.item.JsonItemProcessor;
import com.marklogic.spring.batch.item.JsonItemWriter;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.file.ResourcesItemReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import javax.xml.transform.Source;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.IOException;
import java.util.ArrayList;

@Configuration
@PropertySource("classpath:job.properties")
@EnableBatchProcessing
@Import(com.marklogic.spring.batch.configuration.MarkLogicBatchConfiguration.class)
public class LoadDocumentsFromDirectoryConfig {

    @Autowired
    Environment env;

    @Autowired
    private ApplicationContext ctx;

    @Autowired
    private JobBuilderFactory jobBuilders;

    @Autowired
    private StepBuilderFactory stepBuilders;

    @Autowired
    @Qualifier("target")
    private DatabaseClientProvider jobDatabaseClientProvider;

    private Resource[] resources;

    @Conditional(value = XmlDocumentTypeCondition.class)
    @Bean
    public Job xmlJob(@Qualifier("xmlStep") Step xmlStep) {

        return jobBuilders.get("loadDocumentsFromDirectoryJob").start(xmlStep).build();
    }


    @Conditional(value = JsonDocumentTypeCondition.class)
    @Bean
    public Job jsonJob(@Qualifier("jsonStep") Step jsonStep) {

        return jobBuilders.get("loadDocumentsFromDirectoryJob").start(jsonStep).build();
    }


    @Conditional(value = XmlDocumentTypeCondition.class)
    @Bean
    protected Step xmlStep(ItemReader<Resource> reader, ItemProcessor<Resource, Document> processor, ItemWriter<Document> writer) {

        return stepBuilders.get("xmlStep")
                .<Resource, Document>chunk(10)
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .build();
    }

    @Conditional(value = JsonDocumentTypeCondition.class)
    @Bean
    protected Step jsonStep(ItemReader<Resource> reader, ItemProcessor<Resource, ObjectNode> processor, ItemWriter<ObjectNode> writer) {

        return stepBuilders.get("jsonStep")
                .<Resource, ObjectNode>chunk(10)
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .build();
    }

    @Bean
    public ItemReader<Resource> reader() {
        ResourcesItemReader itemReader = new ResourcesItemReader();
        ArrayList<Resource> resourceList = new ArrayList<Resource>();
        try {
            resources = ctx.getResources(env.getProperty("input_file_path"));
            String inputFilePattern = env.getProperty("input_file_pattern");
            for (int i = 0; i < resources.length; i++) {
                if (resources[i].getFilename().matches(inputFilePattern)) {
                    resourceList.add(resources[i]);
                }
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        itemReader.setResources(resourceList.toArray(new Resource[resourceList.size()]));
        return itemReader;
    }


    @Conditional(value = XmlDocumentTypeCondition.class)
    @Bean
    public ItemProcessor<Resource, Document> xmlProcessor() {
        return new ItemProcessor<Resource, Document>() {
            @Override
            public Document process(Resource item) throws Exception {
                Source source = new StreamSource(item.getFile());
                DOMResult result = new DOMResult();
                TransformerFactory.newInstance().newTransformer().transform(source, result);
                Document doc = (Document) result.getNode();
                XPathFactory factory = XPathFactory.newInstance();
                XPath xpath = factory.newXPath();
                String expression = env.getProperty("uri_id");
                Node node = (Node) xpath.evaluate(expression, doc, XPathConstants.NODE);
                doc.setDocumentURI("/" + node.getTextContent());
                return doc;
            }
        };
    }

    @Conditional(value = XmlDocumentTypeCondition.class)
    @Bean
    public ItemWriter<Document> xmlWriter() {
        return new DocumentItemWriter(jobDatabaseClientProvider.getDatabaseClient());
    }

    @Conditional(value = JsonDocumentTypeCondition.class)
    @Bean
    public ItemProcessor<Resource, ObjectNode> jsonProcessor() {
        return new JsonItemProcessor();
    }

    @Conditional(value = JsonDocumentTypeCondition.class)
    @Bean
    public ItemWriter<ObjectNode> jsonWriter() {
        return new JsonItemWriter(jobDatabaseClientProvider.getDatabaseClient());
    }

}
