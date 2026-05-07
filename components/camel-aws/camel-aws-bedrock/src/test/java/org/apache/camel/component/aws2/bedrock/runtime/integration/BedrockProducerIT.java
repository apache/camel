/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.aws2.bedrock.runtime.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.aws2.bedrock.BedrockModels;
import org.apache.camel.component.aws2.bedrock.runtime.BedrockConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfSystemProperties;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

// Must be manually tested. Provide your own accessKey and secretKey using -Daws.manual.access.key and -Daws.manual.secret.key
@EnabledIfSystemProperties({
        @EnabledIfSystemProperty(named = "aws.manual.access.key", matches = ".*", disabledReason = "Access key not provided"),
        @EnabledIfSystemProperty(named = "aws.manual.secret.key", matches = ".*", disabledReason = "Secret key not provided")
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BedrockProducerIT extends CamelTestSupport {

    @EndpointInject
    private ProducerTemplate template;

    @EndpointInject("mock:result")
    private MockEndpoint result;

    @BeforeEach
    public void resetMocks() {
        result.reset();
    }

    @Test
    public void testInvokeTitanExpressModel() throws InterruptedException {

        result.expectedMessageCount(1);
        final Exchange result = template.send("direct:send_titan_express", exchange -> {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode rootNode = mapper.createObjectNode();
            rootNode.putIfAbsent("inputText",
                    new TextNode(
                            "User: Generate synthetic data for daily product sales in various categories - include row number, product name, category, date of sale and price. Produce output in JSON format. Count records and ensure there are no more than 5."));

            ArrayNode stopSequences = mapper.createArrayNode();
            stopSequences.add("User:");
            ObjectNode childNode = mapper.createObjectNode();
            childNode.putIfAbsent("maxTokenCount", new IntNode(1024));
            childNode.putIfAbsent("stopSequences", stopSequences);
            childNode.putIfAbsent("temperature", new IntNode(0));
            childNode.putIfAbsent("topP", new IntNode(1));

            rootNode.putIfAbsent("textGenerationConfig", childNode);
            exchange.getMessage().setBody(mapper.writer().writeValueAsString(rootNode));
            exchange.getMessage().setHeader(BedrockConstants.MODEL_CONTENT_TYPE, "application/json");
            exchange.getMessage().setHeader(BedrockConstants.MODEL_ACCEPT_CONTENT_TYPE, "application/json");
        });

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testInvokeTitanLiteModel() throws InterruptedException {

        result.expectedMessageCount(1);
        final Exchange result = template.send("direct:send_titan_lite", exchange -> {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode rootNode = mapper.createObjectNode();
            rootNode.putIfAbsent("inputText",
                    new TextNode(
                            "User: Generate synthetic data for daily product sales in various categories - include row number, product name, category, date of sale and price. Produce output in JSON format. Count records and ensure there are no more than 5."));

            ArrayNode stopSequences = mapper.createArrayNode();
            stopSequences.add("User:");
            ObjectNode childNode = mapper.createObjectNode();
            childNode.putIfAbsent("maxTokenCount", new IntNode(1024));
            childNode.putIfAbsent("stopSequences", stopSequences);
            childNode.putIfAbsent("temperature", new IntNode(0));
            childNode.putIfAbsent("topP", new IntNode(1));

            rootNode.putIfAbsent("textGenerationConfig", childNode);
            exchange.getMessage().setBody(mapper.writer().writeValueAsString(rootNode));
            exchange.getMessage().setHeader(BedrockConstants.MODEL_CONTENT_TYPE, "application/json");
            exchange.getMessage().setHeader(BedrockConstants.MODEL_ACCEPT_CONTENT_TYPE, "application/json");
        });

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testInvokeTitanImageModel() throws InterruptedException {

        result.expectedMessageCount(3);
        final Exchange result = template.send("direct:send_titan_image", exchange -> {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode rootNode = mapper.createObjectNode();
            ObjectNode textParameter = mapper.createObjectNode();
            textParameter.putIfAbsent("text",
                    new TextNode("A Sci-fi camel running in the desert"));
            rootNode.putIfAbsent("textToImageParams", textParameter);
            rootNode.putIfAbsent("taskType", new TextNode("TEXT_IMAGE"));
            ObjectNode childNode = mapper.createObjectNode();
            childNode.putIfAbsent("numberOfImages", new IntNode(3));
            childNode.putIfAbsent("quality", new TextNode("standard"));
            childNode.putIfAbsent("cfgScale", new IntNode(8));
            childNode.putIfAbsent("height", new IntNode(512));
            childNode.putIfAbsent("width", new IntNode(512));
            childNode.putIfAbsent("seed", new IntNode(0));

            rootNode.putIfAbsent("imageGenerationConfig", childNode);

            exchange.getMessage().setBody(mapper.writer().writeValueAsString(rootNode));
            exchange.getMessage().setHeader(BedrockConstants.MODEL_CONTENT_TYPE, "application/json");
            exchange.getMessage().setHeader(BedrockConstants.MODEL_ACCEPT_CONTENT_TYPE, "application/json");
        });

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testInvokeTitanEmbeddingsModel() throws InterruptedException {

        result.expectedMessageCount(1);
        final Exchange result = template.send("direct:send_titan_embeddings", exchange -> {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode rootNode = mapper.createObjectNode();
            rootNode.putIfAbsent("inputText",
                    new TextNode("A Sci-fi camel running in the desert"));

            exchange.getMessage().setBody(mapper.writer().writeValueAsString(rootNode));
            exchange.getMessage().setHeader(BedrockConstants.MODEL_CONTENT_TYPE, "application/json");
            exchange.getMessage().setHeader(BedrockConstants.MODEL_ACCEPT_CONTENT_TYPE, "*/*");
        });

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testInvokeJurassic2MidModel() throws InterruptedException {

        result.expectedMessageCount(1);
        final Exchange result = template.send("direct:send_jurassic2_mid_model", exchange -> {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode rootNode = mapper.createObjectNode();
            rootNode.putIfAbsent("prompt",
                    new TextNode(
                            "\"Apple Inc. (NASDAQ:AAPL) Q3 2023 Earnings Conference Call August 3, 2023 5:00 PM ET Operator\\\\nGood day, and welcome to the Apple Q3 Fiscal Year 2023 Earnings Conference Call. Today's call is being recorded. At this time, for opening remarks and introductions, I would like to turn the call over to Saori Casey, Vice President of Finance. Please go ahead.\\\\nSaori Casey\\\\nThank you. Good afternoon, and thank you for joining us. Speaking first today is Apple's CEO, Tim Cook; and he'll be followed by CFO, Luca Maestri. After that, we'll open the call to questions from analysts.\\\\nPlease note that some of the information you'll hear during our discussion today will consist of forward-looking statements, including, without limitation, those regarding revenue, gross margin, operating expenses, other income and expense, taxes, capital allocation and future business outlook, including the potential impact of macroeconomic conditions on the company's business and the results of operations.\\\\nThese statements involve risks and uncertainties that may cause actual results or trends to differ materially from our forecast. For more information, please refer to the risk factors discussed in Apple's most recently filed annual report on Form 10-K and the Form 8-K filed with the SEC today, along with the associated press release. Apple assumes no obligation to update any forward-looking statements, which speak only as of the date they are made.\\\\nI'd now like to turn the call over to Tim for introductory remarks.\\\\nTim Cook\\\\nThank you, Saori. Good afternoon, everyone, and thanks for joining us. Today, Apple is reporting revenue of $81.8 billion for the June quarter, better than our expectations. We continued to see strong results in emerging markets, driven by robust sales of iPhone with June quarter total revenue records in India, Indonesia, Mexico, the Philippines, Poland, Saudi Arabia, Turkey and the UAE. We set June quarter records in a number of other countries as well, including France, the Netherlands and Austria. And we set an all-time revenue record in Services driven by more than $1 billion paid subscriptions.\\\\nWe continued to face an uneven macroeconomic environment, including nearly 4 percentage points of foreign exchange headwinds. On a constant currency basis, we grew compared to the prior year's quarter in aggregate and in the majority of markets we track. We continue to manage deliberately and innovate relentlessly, and we are driven by the sense of possibility those efforts inspire.\\\\nTo that end, before I turn to the quarter in more detail, I want to take a moment to acknowledge the unprecedented innovations we were proud to announce at our Worldwide Developers Conference. In addition to extraordinary new Macs and incredible updates to our software platforms, we had the chance to introduce the world to spatial computing.\\\\nWe were so pleased to share the revolutionary Apple Vision Pro with the world, a bold new product unlike anything else created before. Apple Vision Pro is a marvel of engineering, built on decades of innovation only possible at Apple. It is the most advanced personal electronic device ever created, and we've been thrilled by the reaction from press, analysts, developers and content creators who've had the chance to try it. We can't wait to get it into customers' hands early next year.\\\\nNow let me share more with you on our June quarter results beginning with iPhone. iPhone revenue came in at $39.7 billion for the quarter, down 2% from the year ago quarter's record performance. On a constant currency basis, iPhone revenue grew, and we had a June quarter record for switchers, reflecting the popularity of the iPhone lineup. iPhone 14 customers continue to praise the exceptional battery life and essential health and safety features, while iPhone 14 Plus users are loving the new larger screen size. And with Dynamic Island, Always-On display and the most powerful camera system ever in an iPhone, the iPhone 14 Pro lineup is our best ever.\\\\nTurning to Mac. We recorded $6.8 billion in revenue, down 7% year-over-year. We are proud to have completed the transition of our entire Mac lineup to run exclusively on Apple silicon. We are also excited to have introduced the new 15-inch MacBook Air during the quarter, the world's best 15-inch laptop and one of the best Macs we've ever made. And we launched 2 new powerhouses in computing, Mac Studio with M2 Max and M2 Ultra and Mac Pro with M2 Ultra, which are the most powerful Macs we've ever made.\\\\niPad revenue was $5.8 billion for the June quarter, down 20% year-over-year, in part due to a difficult compare because of the timing of the iPad Air launch last year. Customers are loving iPad's versatility and exceptional value. There was a great deal of excitement from creatives when we brought Final Cut Pro and Logic Pro to iPad this spring. And with the back-to-school season in full swing, iPad has the power to help students tackle the toughest assignments.\\\\nAcross Wearables, Home and Accessories, revenue was $8.3 billion, up 2% year-over-year and in line with our expectations. Packed with features to empower users to live a healthier life, Apple Watch and Apple Watch Ultra continue to help people take the next step on their wellness journey.\\\\nAs I mentioned earlier, last quarter, we held our biggest and most exciting WWDC yet. We were thrilled to welcome developers from across the globe to Apple Park, both in person and virtually, and to share some stunning new announcements with the world.\\\\nIn addition to Apple Vision Pro and the new Macs that we introduced, we had the chance to reveal some truly remarkable new innovations to our software platforms. From exciting new features like Live Voicemail and StandBy in iOS 17, to new tools for users to work, play and personalize their experience in macOS Sonoma and iPadOS 17, to a fresh design and new workout capabilities in watchOS 10, there's so much coming later this year to empower users to get more out of their devices, and we think they're going to instantly love these new features.\\\\nIt was also an exciting quarter for Services where revenue reached $21.2 billion and saw a sequential acceleration to an 8% year-over-year increase, better than we expected. We set an all-time revenue record for total services and in a number of categories, including video, AppleCare, cloud and payment services. Since we introduced Apple Pay almost a decade ago, customers have been loving how easy it is to make purchases online, in apps and in stores. We're also pleased to see Apple Card build on the success of Apple Pay. Designed with our users' financial health in mind, Apple Card has become one of the most successful credit card programs in the U.S. with award-winning customer satisfaction. And this spring, we introduced a new high-yield savings account for Apple Card customers, which has become incredibly popular, with customers already making more than $10 billion in deposits.\\\\nMeanwhile, Apple TV+ continues to provide a spectacular showcase of imaginative storytelling. Recently, fans welcomed new series like Hijack and Silo as well as returning fan favorites like Foundation and The Afterparty. In the few years since its launch, Apple TV+ has earned more than 1,500 nominations and 370 wins. That includes the 54 Emmy Award nominations across 13 titles that Apple TV+ received last month.\\\\nIt's also been an exciting time for sports on Apple TV+. Soccer legend Lionel Messi made his debut with Major League Soccer last month, and fans all over the world tuned in with MLS Season Pass. We are excited about our MLS partnership, and we're thrilled to see Messi suiting up with Inter Miami.\\\\nAnd just in time for summer concert season, Apple Music launched new discovery features celebrating live music, including venue guides in Apple Maps and set lists from tours of major artists. These new features and others join a lineup of updates coming later this year to make Services more powerful, more useful and more fun than ever.\\\\nEverything we do is in service of our customers, and retail is where we bring the best of Apple. During the quarter, we opened the Apple Store online in Vietnam, and we're excited to connect with more customers there. We also redesigned our first-ever Apple Store located in Tysons Corner, Northern Virginia, with inclusive, innovative and sustainable design enhancements. We opened a beautiful new store beneath our new London headquarters in the historic Battersea Power Station. And the performance of the stores we opened in India this spring exceeded our initial expectations.\\\\nWith every product we create, every feature we develop and every interaction we share with our customers, we lead with the values we stand for. We believe in creating technology that serves all of humanity, which is why accessibility has always been a core value that we embed in everything we do.\\\\nOn Global Accessibility Awareness Day, we unveiled some extraordinary new tools for cognitive, vision, hearing and mobile accessibility that will be available later this year, including Assistive Access, which distills apps to their most essential features, and Personal Voice, which allows users to create a synthesized voice that sounds just like them.\\\\nBuilding technology and service of our customers also means protecting their privacy, which we believe is a fundamental human right. That's why we were pleased to announce major updates to Safari Private Browsing, Communication Safety and Lockdown Mode to further safeguard our users. And as part of our efforts to build a better world, we announced that we've more than doubled our initial commitment to our Racial Equity and Justice Initiative to more than $200 million. We will continue to do our part to support education, economic empowerment and criminal justice reform work. And while supporting efforts to advance equity and opportunity, we continue to build a culture of belonging at Apple and a workforce that reflects the communities we serve.\\\\nThrough our environmental work, we're making strides in our commitment to leave the world better than we found it. Last month, Apple joined with global nonprofit Acumen in a new effort to improve livelihoods in India through clean energy innovation, and we are as committed as ever to our Apple 2030 goal to be carbon neutral across our entire supply chain and the life cycle of our products.\\\\nWe've long held that education is the great equalizer. With that in mind, we're expanding Apple Learning Coach, a free professional learning program that teaches educators how to get more out of Apple technology in the classroom. Today, we welcome more than 1,900 educators across the U.S. to the program. By the end of the year, we'll offer Apple Learning Coach in 12 more countries.\\\\nAs we're connecting with teachers, we're also celebrating the graduations of students at our app developer academies around the world. From Detroit, to Naples, to Riyadh and more, we're excited to watch these talented developers embark on careers in coding and find ways to make a positive difference in their communities.\\\\nApple remains a champion of innovation, a company fueled by boundless creativity, driven by a deep sense of mission and guided by the unshakable belief that a great idea can change the world. Looking ahead, we'll continue to manage for the long term, always pushing the limits of what's possible and always putting the customer at the center of everything we do.\\\\nWith that, I'll turn it over to Luca.\\\\nLuca Maestri\\\\nThank you, Tim, and good afternoon, everyone. Revenue for the June quarter was $81.8 billion, down 1% from last year and better than our expectations despite nearly 4 percentage points of negative impact from foreign exchange. On a constant currency basis, our revenue grew year-over-year in total and in the majority of the markets we track. We set June quarter records in both Europe and Greater China and continue to see strong performance across our emerging markets driven by iPhone.\\\\nProducts revenue was $60.6 billion, down 4% from last year, as we faced FX headwinds and an uneven macroeconomic environment. However, our installed base reached an all-time high across all geographic segments, driven by a June quarter record for iPhone switchers and high new-to rates in Mac, iPad and Watch, coupled with very high levels of customer satisfaction and loyalty.\\\\nOur Services revenue set an all-time record of $21.2 billion, up 8% year-over-year and grew double digits in constant currency. Our performance was strong around the world as we reach all-time Services revenue records in Americas and Europe and June quarter records in Greater China and rest of Asia Pacific.\\\\nCompany gross margin was 44.5%, a record level for the June quarter and up 20 basis points sequentially, driven by cost savings and favorable mix shift towards Services, partially offset by a seasonal loss of leverage. Products gross margin was 35.4%, down 130 basis points from last quarter due to seasonal loss of leverage and mix, partially offset by favorable costs. Services gross margin was 70.5%, decreasing 50 basis points sequentially.\\\\nOperating expenses of $13.4 billion were below the low end of the guidance range we provided at the beginning of the quarter and decelerated from the March quarter. We continue to take a deliberate approach in managing our spend with strong focus on innovation and new product development. The results of these actions delivered net income of $19.9 billion, diluted earnings per share of $1.26, up 5% versus last year, and very strong operating cash flow of $26.4 billion.\\\\nLet me now provide more detail for each of our revenue categories. iPhone revenue was $39.7 billion, down 2% year-over-year but grew on a constant currency basis. We set revenue records in several markets around the world, including an all-time record in India and June quarter records in Latin America, the Middle East and Africa, Indonesia, the Philippines, Italy, the Netherlands and the U.K. Our iPhone active installed base grew to a new all-time high, thanks to a June quarter record in switchers. This is a testament to our extremely high levels of customer satisfaction, which 451 Research recently measured at 98% for the iPhone 14 family in the U.S.\\\\nMac generated $6.8 billion in revenue, down 7% year-over-year. We continue to invest in our Mac portfolio. And this past quarter, we were pleased to complete the transition to Apple silicon for the entire lineup. This transition has driven both strong upgrade activity and a high number of new customers. In fact, almost half of Mac buyers during the quarter were new to the product. We also saw reported customer satisfaction of 96% for Mac in the U.S.\\\\niPad revenue was $5.8 billion, down 20% year-over-year and in line with our expectations. These results were driven by a difficult compare against the full quarter impact of the iPad Air launch in the prior year. At the same time, we continue to attract a large number of new customers to the iPad installed base with over half of the customers who purchased iPads during the quarter being new to the product. And the latest reports from 451 Research indicate customer satisfaction of 96% in the U.S.\\\\nWearables, Home and Accessories revenue was $8.3 billion, up 2% year-over-year, with a June quarter record in Greater China and strong performance in several emerging markets. We continue to see Apple Watch expand its reach with about 2/3 of customers purchasing an Apple Watch during the quarter being new to the product. And this is combined with very high levels of customer satisfaction, which was recently reported at 98% in the United States.\\\\nMoving on to Services. We reached a new all-time revenue record of $21.2 billion with year-over-year growth accelerating sequentially to 8% and up double digits in constant currency. In addition to the all-time records Tim mentioned earlier, we also set June quarter records for advertising, App Store and Music. We are very pleased with our performance in Services, which is a direct reflection of our ecosystem's strength.\\\\nFirst, our installed base of over 2 billion active devices continues to grow at a nice pace and establishes a solid foundation for the future expansion of our ecosystem. Second, we see increased customer engagement with our services. Both our transacting accounts and paid accounts grew double digits year-over-year, each reaching a new all-time high. Third, our paid subscriptions showed strong growth. This past quarter, we reached an important milestone and passed 1 billion paid subscriptions across the services on our platform, up 150 million during the last 12 months and nearly double the number of paid subscriptions we had only 3 years ago. And finally, we continue to improve the breadth and the quality of our current services. From 20 new games on Apple Arcade, to brand-new content on Apple TV+, to the launch of our high-yield savings account with Apple Card, our customers are loving these enhanced offerings.\\\\nTurning to the enterprise market. Our customers are leveraging Apple products every day to help improve productivity and attract talent. Blackstone, a global investment management firm, is expanding its Apple footprint from their corporate iPhone fleet to now offering the MacBook Air powered by M2 to all of their corporate employees and portfolio companies. Gilead, a leading biopharmaceutical company, has deployed thousands of iPads globally to their sales team. Over the last 6 months, they have also doubled their Mac user base by making MacBook Air available to more employees with a focus on user experience and strong security.\\\\nLet me now turn to our cash position and capital return program. We ended the quarter with over $166 billion in cash and marketable securities. We repaid $7.5 billion in maturing debt while issuing $5.2 billion of new debt and increasing commercial paper by $2 billion, leaving us with total debt of $109 billion. As a result, net cash was $57 billion at the end of the quarter.\\\\nDuring the quarter, we returned over $24 billion to shareholders, including $3.8 billion in dividends and equivalents and $18 billion through open market repurchases of 103 million Apple shares. We continue to believe there is great value in our stock and maintain our target of reaching a net cash neutral position over time.\\\\nAs we move ahead into the September quarter, I'd like to review our outlook, which includes the types of forward-looking information that Saori referred to at the beginning of the call. We expect our September quarter year-over-year revenue performance to be similar to the June quarter, assuming that the macroeconomic outlook doesn't worsen from what we are projecting today for the current quarter. Foreign exchange will continue to be a headwind, and we expect a negative year-over-year revenue impact of over 2 percentage points.\\\\nWe expect iPhone and Services year-over-year performance to accelerate from the June quarter. Also, we expect the revenue for both Mac and iPad to decline by double digits year-over-year due to difficult compares, particularly on the Mac. For both products, we experienced supply disruptions from factory shutdowns in the June quarter a year ago and were able to fulfill significant pent-up demand in the year ago September quarter.\\\\nWe expect gross margin to be between 44% and 45%. We expect OpEx to be between $13.5 billion and $13.7 billion. We expect OI&E to be around negative $250 million, excluding any potential impact from the mark-to-market of minority investments, and our tax rate to be around 16%.\\\\nFinally, today, our Board of Directors has declared a cash dividend of $0.24 per share of common stock payable on August 17, 2023, to shareholders of record as of August 14, 2023. With that, let's open the call to questions.\\\\n###\\\\nSummarize the above conversation.\\\""));

            rootNode.putIfAbsent("maxTokens", new IntNode(500));
            rootNode.putIfAbsent("temperature", new IntNode(0));
            rootNode.putIfAbsent("topP", new IntNode(1));

            ArrayNode stopSequences = mapper.createArrayNode();
            stopSequences.add("###");

            rootNode.putIfAbsent("stopSequences", stopSequences);

            ObjectNode childNode = mapper.createObjectNode();
            childNode.putIfAbsent("scale", new IntNode(0));

            rootNode.putIfAbsent("presencePenalty", childNode);
            rootNode.putIfAbsent("frequencyPenalty", childNode);

            exchange.getMessage().setBody(mapper.writer().writeValueAsString(rootNode));
            exchange.getMessage().setHeader(BedrockConstants.MODEL_CONTENT_TYPE, "application/json");
            exchange.getMessage().setHeader(BedrockConstants.MODEL_ACCEPT_CONTENT_TYPE, "application/json");
        });

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testInvokeAnthropicV1Model() throws InterruptedException {

        result.expectedMessageCount(1);
        final Exchange result = template.send("direct:send_anthropic_v1_model", exchange -> {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode rootNode = mapper.createObjectNode();
            rootNode.putIfAbsent("prompt",
                    new TextNode("Human: Can you tell the history of Mayflower? \\n\\Assistant:"));

            ArrayNode stopSequences = mapper.createArrayNode();
            stopSequences.add("Human:");
            rootNode.putIfAbsent("max_tokens_to_sample", new IntNode(300));
            rootNode.putIfAbsent("stop_sequences", stopSequences);
            rootNode.putIfAbsent("temperature", new DoubleNode(0.5));
            rootNode.putIfAbsent("top_p", new IntNode(1));
            rootNode.putIfAbsent("top_k", new IntNode(250));
            rootNode.putIfAbsent("anthropic_version", new TextNode("bedrock-2023-05-31"));

            exchange.getMessage().setBody(mapper.writer().writeValueAsString(rootNode));
            exchange.getMessage().setHeader(BedrockConstants.MODEL_CONTENT_TYPE, "application/json");
            exchange.getMessage().setHeader(BedrockConstants.MODEL_ACCEPT_CONTENT_TYPE, "application/json");
        });

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testInvokeAnthropicV2Model() throws InterruptedException {

        result.expectedMessageCount(1);
        final Exchange result = template.send("direct:send_anthropic_v2_model", exchange -> {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode rootNode = mapper.createObjectNode();
            rootNode.putIfAbsent("prompt",
                    new TextNode("Human: Can you tell the history of Mayflower? \\n\\Assistant:"));

            ArrayNode stopSequences = mapper.createArrayNode();
            stopSequences.add("Human:");
            rootNode.putIfAbsent("max_tokens_to_sample", new IntNode(300));
            rootNode.putIfAbsent("stop_sequences", stopSequences);
            rootNode.putIfAbsent("temperature", new DoubleNode(0.5));
            rootNode.putIfAbsent("top_p", new IntNode(1));
            rootNode.putIfAbsent("top_k", new IntNode(250));
            rootNode.putIfAbsent("anthropic_version", new TextNode("bedrock-2023-05-31"));

            exchange.getMessage().setBody(mapper.writer().writeValueAsString(rootNode));
            exchange.getMessage().setHeader(BedrockConstants.MODEL_CONTENT_TYPE, "application/json");
            exchange.getMessage().setHeader(BedrockConstants.MODEL_ACCEPT_CONTENT_TYPE, "application/json");
        });

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testInvokeAnthropicV21Model() throws InterruptedException {

        result.expectedMessageCount(1);
        final Exchange result = template.send("direct:send_anthropic_v21_model", exchange -> {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode rootNode = mapper.createObjectNode();
            rootNode.putIfAbsent("prompt",
                    new TextNode("Human: Can you tell the history of Mayflower? \\n\\Assistant:"));

            ArrayNode stopSequences = mapper.createArrayNode();
            stopSequences.add("Human:");
            rootNode.putIfAbsent("max_tokens_to_sample", new IntNode(300));
            rootNode.putIfAbsent("stop_sequences", stopSequences);
            rootNode.putIfAbsent("temperature", new DoubleNode(0.5));
            rootNode.putIfAbsent("top_p", new IntNode(1));
            rootNode.putIfAbsent("top_k", new IntNode(250));
            rootNode.putIfAbsent("anthropic_version", new TextNode("bedrock-2023-05-31"));

            exchange.getMessage().setBody(mapper.writer().writeValueAsString(rootNode));
            exchange.getMessage().setHeader(BedrockConstants.MODEL_CONTENT_TYPE, "application/json");
            exchange.getMessage().setHeader(BedrockConstants.MODEL_ACCEPT_CONTENT_TYPE, "application/json");
        });

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testInvokeAnthropicV3Model() throws InterruptedException {

        result.expectedMessageCount(1);
        final Exchange result = template.send("direct:send_anthropic_v3_model", exchange -> {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode rootNode = mapper.createObjectNode();

            ArrayNode messages = mapper.createArrayNode();

            ObjectNode element = mapper.createObjectNode();
            element.putIfAbsent("role", new TextNode("user"));

            ArrayNode content = mapper.createArrayNode();

            ObjectNode textContent = mapper.createObjectNode();

            textContent.putIfAbsent("type", new TextNode("text"));
            textContent.putIfAbsent("text", new TextNode("Can you tell the history of Mayflower?"));

            content.add(textContent);

            element.putIfAbsent("content", content);

            messages.add(element);

            rootNode.putIfAbsent("messages", messages);
            rootNode.putIfAbsent("max_tokens", new IntNode(1000));
            rootNode.putIfAbsent("anthropic_version", new TextNode("bedrock-2023-05-31"));

            exchange.getMessage().setBody(mapper.writer().writeValueAsString(rootNode));
            exchange.getMessage().setHeader(BedrockConstants.MODEL_CONTENT_TYPE, "application/json");
            exchange.getMessage().setHeader(BedrockConstants.MODEL_ACCEPT_CONTENT_TYPE, "application/json");
        });

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testInvokeAnthropicV3HaikuModel() throws InterruptedException {

        result.expectedMessageCount(1);
        final Exchange result = template.send("direct:send_anthropic_v3_haiku_model", exchange -> {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode rootNode = mapper.createObjectNode();

            ArrayNode messages = mapper.createArrayNode();

            ObjectNode element = mapper.createObjectNode();
            element.putIfAbsent("role", new TextNode("user"));

            ArrayNode content = mapper.createArrayNode();

            ObjectNode textContent = mapper.createObjectNode();

            textContent.putIfAbsent("type", new TextNode("text"));
            textContent.putIfAbsent("text", new TextNode("Can you tell the history of Mayflower?"));

            content.add(textContent);

            element.putIfAbsent("content", content);

            messages.add(element);

            rootNode.putIfAbsent("messages", messages);
            rootNode.putIfAbsent("max_tokens", new IntNode(1000));
            rootNode.putIfAbsent("anthropic_version", new TextNode("bedrock-2023-05-31"));

            exchange.getMessage().setBody(mapper.writer().writeValueAsString(rootNode));
            exchange.getMessage().setHeader(BedrockConstants.MODEL_CONTENT_TYPE, "application/json");
            exchange.getMessage().setHeader(BedrockConstants.MODEL_ACCEPT_CONTENT_TYPE, "application/json");
        });

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testInvokeMistral7BInstructModel() throws InterruptedException {

        result.expectedMessageCount(1);
        final Exchange result = template.send("direct:send_mistral_7b_instruct_model", exchange -> {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode rootNode = mapper.createObjectNode();
            rootNode.putIfAbsent("prompt",
                    new TextNode("\"<s>[INST] Can you tell the history of Mayflower? [/INST]\\\""));

            rootNode.putIfAbsent("max_tokens", new IntNode(300));
            rootNode.putIfAbsent("temperature", new DoubleNode(0.5));
            rootNode.putIfAbsent("top_p", new DoubleNode(0.9));
            rootNode.putIfAbsent("top_k", new IntNode(50));

            exchange.getMessage().setBody(mapper.writer().writeValueAsString(rootNode));
            exchange.getMessage().setHeader(BedrockConstants.MODEL_CONTENT_TYPE, "application/json");
            exchange.getMessage().setHeader(BedrockConstants.MODEL_ACCEPT_CONTENT_TYPE, "application/json");
        });

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testInvokeMistral8x7BInstructModel() throws InterruptedException {

        result.expectedMessageCount(1);
        final Exchange result = template.send("direct:send_mistral_8x7b_instruct_model", exchange -> {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode rootNode = mapper.createObjectNode();
            rootNode.putIfAbsent("prompt",
                    new TextNode("\"<s>[INST] Can you tell the history of Mayflower? [/INST]\\\""));

            rootNode.putIfAbsent("max_tokens", new IntNode(300));
            rootNode.putIfAbsent("temperature", new DoubleNode(0.5));
            rootNode.putIfAbsent("top_p", new DoubleNode(0.9));
            rootNode.putIfAbsent("top_k", new IntNode(50));

            exchange.getMessage().setBody(mapper.writer().writeValueAsString(rootNode));
            exchange.getMessage().setHeader(BedrockConstants.MODEL_CONTENT_TYPE, "application/json");
            exchange.getMessage().setHeader(BedrockConstants.MODEL_ACCEPT_CONTENT_TYPE, "application/json");
        });

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testInvokeMistralLargeModel() throws InterruptedException {

        result.expectedMessageCount(1);
        final Exchange result = template.send("direct:send_mistral_large_model", exchange -> {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode rootNode = mapper.createObjectNode();
            rootNode.putIfAbsent("prompt",
                    new TextNode("\"<s>[INST] Can you tell the history of Mayflower? [/INST]\\\""));

            rootNode.putIfAbsent("max_tokens", new IntNode(200));
            rootNode.putIfAbsent("temperature", new DoubleNode(0.5));
            rootNode.putIfAbsent("top_p", new DoubleNode(0.9));
            rootNode.putIfAbsent("top_k", new IntNode(50));

            exchange.getMessage().setBody(mapper.writer().writeValueAsString(rootNode));
            exchange.getMessage().setHeader(BedrockConstants.MODEL_CONTENT_TYPE, "application/json");
            exchange.getMessage().setHeader(BedrockConstants.MODEL_ACCEPT_CONTENT_TYPE, "application/json");
        });

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testInvokeTitanMultimodalEmbeddingsModel() throws InterruptedException {

        result.expectedMessageCount(1);
        final Exchange result = template.send("direct:send_titan_multimodal_embeddings", exchange -> {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode rootNode = mapper.createObjectNode();
            rootNode.putIfAbsent("inputText",
                    new TextNode("A Sci-fi camel running in the desert"));

            exchange.getMessage().setBody(mapper.writer().writeValueAsString(rootNode));
            exchange.getMessage().setHeader(BedrockConstants.MODEL_CONTENT_TYPE, "application/json");
            exchange.getMessage().setHeader(BedrockConstants.MODEL_ACCEPT_CONTENT_TYPE, "*/*");
        });

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testInvokeTitanTextEmbeddingsV2Model() throws InterruptedException {

        result.expectedMessageCount(1);
        final Exchange result = template.send("direct:send_titan_text_embeddings_v2", exchange -> {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode rootNode = mapper.createObjectNode();
            rootNode.putIfAbsent("inputText",
                    new TextNode("A Sci-fi camel running in the desert"));

            exchange.getMessage().setBody(mapper.writer().writeValueAsString(rootNode));
            exchange.getMessage().setHeader(BedrockConstants.MODEL_CONTENT_TYPE, "application/json");
            exchange.getMessage().setHeader(BedrockConstants.MODEL_ACCEPT_CONTENT_TYPE, "*/*");
        });

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testInvokeTitanPremierModel() throws InterruptedException {

        result.expectedMessageCount(1);
        final Exchange result = template.send("direct:send_titan_premier", exchange -> {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode rootNode = mapper.createObjectNode();
            rootNode.putIfAbsent("inputText",
                    new TextNode(
                            "User: Generate synthetic data for daily product sales in various categories - include row number, product name, category, date of sale and price. Produce output in JSON format. Count records and ensure there are no more than 5."));

            ArrayNode stopSequences = mapper.createArrayNode();
            stopSequences.add("User:");
            ObjectNode childNode = mapper.createObjectNode();
            childNode.putIfAbsent("maxTokenCount", new IntNode(1024));
            childNode.putIfAbsent("stopSequences", stopSequences);
            childNode.putIfAbsent("temperature", new DoubleNode(0.7));
            childNode.putIfAbsent("topP", new DoubleNode(0.9));

            rootNode.putIfAbsent("textGenerationConfig", childNode);
            exchange.getMessage().setBody(mapper.writer().writeValueAsString(rootNode));
            exchange.getMessage().setHeader(BedrockConstants.MODEL_CONTENT_TYPE, "application/json");
            exchange.getMessage().setHeader(BedrockConstants.MODEL_ACCEPT_CONTENT_TYPE, "application/json");
        });

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testInvokeNovaLiteModel() throws InterruptedException {

        result.expectedMessageCount(1);
        final Exchange result = template.send("direct:send_nova_lite", exchange -> {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode rootNode = mapper.createObjectNode();

            ArrayNode messages = mapper.createArrayNode();

            ObjectNode element = mapper.createObjectNode();
            element.putIfAbsent("role", new TextNode("user"));

            ArrayNode content = mapper.createArrayNode();

            ObjectNode textContent = mapper.createObjectNode();

            textContent.putIfAbsent("type", new TextNode("text"));
            textContent.putIfAbsent("text", new TextNode("Can you tell the history of Mayflower?"));

            content.add(textContent);

            element.putIfAbsent("content", content);

            messages.add(element);

            rootNode.putIfAbsent("messages", messages);
            rootNode.putIfAbsent("max_tokens", new IntNode(1000));

            exchange.getMessage().setBody(mapper.writer().writeValueAsString(rootNode));
            exchange.getMessage().setHeader(BedrockConstants.MODEL_CONTENT_TYPE, "application/json");
            exchange.getMessage().setHeader(BedrockConstants.MODEL_ACCEPT_CONTENT_TYPE, "application/json");
        });

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testInvokeJamba15LargeModel() throws InterruptedException {

        result.expectedMessageCount(1);
        final Exchange result = template.send("direct:send_jamba_15_large", exchange -> {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode rootNode = mapper.createObjectNode();

            ArrayNode messages = mapper.createArrayNode();

            ObjectNode element = mapper.createObjectNode();
            element.putIfAbsent("role", new TextNode("user"));
            element.putIfAbsent("content", new TextNode("Can you tell the history of Mayflower?"));

            messages.add(element);

            rootNode.putIfAbsent("messages", messages);
            rootNode.putIfAbsent("max_tokens", new IntNode(1000));
            rootNode.putIfAbsent("temperature", new DoubleNode(0.7));
            rootNode.putIfAbsent("top_p", new DoubleNode(0.9));

            exchange.getMessage().setBody(mapper.writer().writeValueAsString(rootNode));
            exchange.getMessage().setHeader(BedrockConstants.MODEL_CONTENT_TYPE, "application/json");
            exchange.getMessage().setHeader(BedrockConstants.MODEL_ACCEPT_CONTENT_TYPE, "application/json");
        });

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testInvokeCohereCommandRModel() throws InterruptedException {

        result.expectedMessageCount(1);
        final Exchange result = template.send("direct:send_cohere_command_r", exchange -> {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode rootNode = mapper.createObjectNode();
            rootNode.putIfAbsent("message", new TextNode("Can you tell the history of Mayflower?"));
            rootNode.putIfAbsent("max_tokens", new IntNode(1000));
            rootNode.putIfAbsent("temperature", new DoubleNode(0.7));
            rootNode.putIfAbsent("p", new DoubleNode(0.9));

            exchange.getMessage().setBody(mapper.writer().writeValueAsString(rootNode));
            exchange.getMessage().setHeader(BedrockConstants.MODEL_CONTENT_TYPE, "application/json");
            exchange.getMessage().setHeader(BedrockConstants.MODEL_ACCEPT_CONTENT_TYPE, "application/json");
        });

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testInvokeLlama38BInstructModel() throws InterruptedException {

        result.expectedMessageCount(1);
        final Exchange result = template.send("direct:send_llama3_8b_instruct", exchange -> {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode rootNode = mapper.createObjectNode();
            rootNode.putIfAbsent("prompt", new TextNode(
                    "<|begin_of_text|><|start_header_id|>user<|end_header_id|>\n\nCan you tell the history of Mayflower?<|eot_id|><|start_header_id|>assistant<|end_header_id|>\n\n"));
            rootNode.putIfAbsent("max_gen_len", new IntNode(1000));
            rootNode.putIfAbsent("temperature", new DoubleNode(0.7));
            rootNode.putIfAbsent("top_p", new DoubleNode(0.9));

            exchange.getMessage().setBody(mapper.writer().writeValueAsString(rootNode));
            exchange.getMessage().setHeader(BedrockConstants.MODEL_CONTENT_TYPE, "application/json");
            exchange.getMessage().setHeader(BedrockConstants.MODEL_ACCEPT_CONTENT_TYPE, "application/json");
        });

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testInvokeMistralLarge2407Model() throws InterruptedException {

        result.expectedMessageCount(1);
        final Exchange result = template.send("direct:send_mistral_large_2407", exchange -> {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode rootNode = mapper.createObjectNode();
            rootNode.putIfAbsent("prompt",
                    new TextNode("\"<s>[INST] Can you tell the history of Mayflower? [/INST]\\\""));

            rootNode.putIfAbsent("max_tokens", new IntNode(300));
            rootNode.putIfAbsent("temperature", new DoubleNode(0.5));
            rootNode.putIfAbsent("top_p", new DoubleNode(0.9));
            rootNode.putIfAbsent("top_k", new IntNode(50));

            exchange.getMessage().setBody(mapper.writer().writeValueAsString(rootNode));
            exchange.getMessage().setHeader(BedrockConstants.MODEL_CONTENT_TYPE, "application/json");
            exchange.getMessage().setHeader(BedrockConstants.MODEL_ACCEPT_CONTENT_TYPE, "application/json");
        });

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testInvokeNovaMicroModel() throws InterruptedException {

        result.expectedMessageCount(1);
        final Exchange result = template.send("direct:send_nova_micro", exchange -> {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode rootNode = mapper.createObjectNode();

            ArrayNode messages = mapper.createArrayNode();

            ObjectNode element = mapper.createObjectNode();
            element.putIfAbsent("role", new TextNode("user"));

            ArrayNode content = mapper.createArrayNode();

            ObjectNode textContent = mapper.createObjectNode();

            textContent.putIfAbsent("type", new TextNode("text"));
            textContent.putIfAbsent("text", new TextNode("Can you tell the history of Mayflower?"));

            content.add(textContent);

            element.putIfAbsent("content", content);

            messages.add(element);

            rootNode.putIfAbsent("messages", messages);
            rootNode.putIfAbsent("max_tokens", new IntNode(1000));

            exchange.getMessage().setBody(mapper.writer().writeValueAsString(rootNode));
            exchange.getMessage().setHeader(BedrockConstants.MODEL_CONTENT_TYPE, "application/json");
            exchange.getMessage().setHeader(BedrockConstants.MODEL_ACCEPT_CONTENT_TYPE, "application/json");
        });

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testInvokeNovaProModel() throws InterruptedException {

        result.expectedMessageCount(1);
        final Exchange result = template.send("direct:send_nova_pro", exchange -> {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode rootNode = mapper.createObjectNode();

            ArrayNode messages = mapper.createArrayNode();

            ObjectNode element = mapper.createObjectNode();
            element.putIfAbsent("role", new TextNode("user"));

            ArrayNode content = mapper.createArrayNode();

            ObjectNode textContent = mapper.createObjectNode();

            textContent.putIfAbsent("type", new TextNode("text"));
            textContent.putIfAbsent("text", new TextNode("Can you tell the history of Mayflower?"));

            content.add(textContent);

            element.putIfAbsent("content", content);

            messages.add(element);

            rootNode.putIfAbsent("messages", messages);
            rootNode.putIfAbsent("max_tokens", new IntNode(1000));

            exchange.getMessage().setBody(mapper.writer().writeValueAsString(rootNode));
            exchange.getMessage().setHeader(BedrockConstants.MODEL_CONTENT_TYPE, "application/json");
            exchange.getMessage().setHeader(BedrockConstants.MODEL_ACCEPT_CONTENT_TYPE, "application/json");
        });

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testInvokeJamba15MiniModel() throws InterruptedException {

        result.expectedMessageCount(1);
        final Exchange result = template.send("direct:send_jamba_15_mini", exchange -> {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode rootNode = mapper.createObjectNode();

            ArrayNode messages = mapper.createArrayNode();

            ObjectNode element = mapper.createObjectNode();
            element.putIfAbsent("role", new TextNode("user"));
            element.putIfAbsent("content", new TextNode("Can you tell the history of Mayflower?"));

            messages.add(element);

            rootNode.putIfAbsent("messages", messages);
            rootNode.putIfAbsent("max_tokens", new IntNode(1000));
            rootNode.putIfAbsent("temperature", new DoubleNode(0.7));
            rootNode.putIfAbsent("top_p", new DoubleNode(0.9));

            exchange.getMessage().setBody(mapper.writer().writeValueAsString(rootNode));
            exchange.getMessage().setHeader(BedrockConstants.MODEL_CONTENT_TYPE, "application/json");
            exchange.getMessage().setHeader(BedrockConstants.MODEL_ACCEPT_CONTENT_TYPE, "application/json");
        });

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testInvokeCohereCommandRPlusModel() throws InterruptedException {

        result.expectedMessageCount(1);
        final Exchange result = template.send("direct:send_cohere_command_r_plus", exchange -> {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode rootNode = mapper.createObjectNode();
            rootNode.putIfAbsent("message", new TextNode("Can you tell the history of Mayflower?"));
            rootNode.putIfAbsent("max_tokens", new IntNode(1000));
            rootNode.putIfAbsent("temperature", new DoubleNode(0.7));
            rootNode.putIfAbsent("p", new DoubleNode(0.9));

            exchange.getMessage().setBody(mapper.writer().writeValueAsString(rootNode));
            exchange.getMessage().setHeader(BedrockConstants.MODEL_CONTENT_TYPE, "application/json");
            exchange.getMessage().setHeader(BedrockConstants.MODEL_ACCEPT_CONTENT_TYPE, "application/json");
        });

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testInvokeLlama31_70BInstructModel() throws InterruptedException {

        result.expectedMessageCount(1);
        final Exchange result = template.send("direct:send_llama31_70b_instruct", exchange -> {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode rootNode = mapper.createObjectNode();
            rootNode.putIfAbsent("prompt", new TextNode(
                    "<|begin_of_text|><|start_header_id|>user<|end_header_id|>\n\nCan you tell the history of Mayflower?<|eot_id|><|start_header_id|>assistant<|end_header_id|>\n\n"));
            rootNode.putIfAbsent("max_gen_len", new IntNode(1000));
            rootNode.putIfAbsent("temperature", new DoubleNode(0.7));
            rootNode.putIfAbsent("top_p", new DoubleNode(0.9));

            exchange.getMessage().setBody(mapper.writer().writeValueAsString(rootNode));
            exchange.getMessage().setHeader(BedrockConstants.MODEL_CONTENT_TYPE, "application/json");
            exchange.getMessage().setHeader(BedrockConstants.MODEL_ACCEPT_CONTENT_TYPE, "application/json");
        });

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testInvokeLlama32_11BInstructModel() throws InterruptedException {

        result.expectedMessageCount(1);
        final Exchange result = template.send("direct:send_llama32_11b_instruct", exchange -> {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode rootNode = mapper.createObjectNode();
            rootNode.putIfAbsent("prompt", new TextNode(
                    "<|begin_of_text|><|start_header_id|>user<|end_header_id|>\n\nCan you tell the history of Mayflower?<|eot_id|><|start_header_id|>assistant<|end_header_id|>\n\n"));
            rootNode.putIfAbsent("max_gen_len", new IntNode(1000));
            rootNode.putIfAbsent("temperature", new DoubleNode(0.7));
            rootNode.putIfAbsent("top_p", new DoubleNode(0.9));

            exchange.getMessage().setBody(mapper.writer().writeValueAsString(rootNode));
            exchange.getMessage().setHeader(BedrockConstants.MODEL_CONTENT_TYPE, "application/json");
            exchange.getMessage().setHeader(BedrockConstants.MODEL_ACCEPT_CONTENT_TYPE, "application/json");
        });

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testInvokeAnthropicClaude35Sonnet2Model() throws InterruptedException {

        result.expectedMessageCount(1);
        final Exchange result = template.send("direct:send_anthropic_claude_35_sonnet_2", exchange -> {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode rootNode = mapper.createObjectNode();

            ArrayNode messages = mapper.createArrayNode();

            ObjectNode element = mapper.createObjectNode();
            element.putIfAbsent("role", new TextNode("user"));

            ArrayNode content = mapper.createArrayNode();

            ObjectNode textContent = mapper.createObjectNode();

            textContent.putIfAbsent("type", new TextNode("text"));
            textContent.putIfAbsent("text", new TextNode("Can you tell the history of Mayflower?"));

            content.add(textContent);

            element.putIfAbsent("content", content);

            messages.add(element);

            rootNode.putIfAbsent("messages", messages);
            rootNode.putIfAbsent("max_tokens", new IntNode(1000));
            rootNode.putIfAbsent("anthropic_version", new TextNode("bedrock-2023-05-31"));

            exchange.getMessage().setBody(mapper.writer().writeValueAsString(rootNode));
            exchange.getMessage().setHeader(BedrockConstants.MODEL_CONTENT_TYPE, "application/json");
            exchange.getMessage().setHeader(BedrockConstants.MODEL_ACCEPT_CONTENT_TYPE, "application/json");
        });

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testInvokeAnthropicClaude35HaikuModel() throws InterruptedException {

        result.expectedMessageCount(1);
        final Exchange result = template.send("direct:send_anthropic_claude_35_haiku", exchange -> {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode rootNode = mapper.createObjectNode();

            ArrayNode messages = mapper.createArrayNode();

            ObjectNode element = mapper.createObjectNode();
            element.putIfAbsent("role", new TextNode("user"));

            ArrayNode content = mapper.createArrayNode();

            ObjectNode textContent = mapper.createObjectNode();

            textContent.putIfAbsent("type", new TextNode("text"));
            textContent.putIfAbsent("text", new TextNode("Can you tell the history of Mayflower?"));

            content.add(textContent);

            element.putIfAbsent("content", content);

            messages.add(element);

            rootNode.putIfAbsent("messages", messages);
            rootNode.putIfAbsent("max_tokens", new IntNode(1000));
            rootNode.putIfAbsent("anthropic_version", new TextNode("bedrock-2023-05-31"));

            exchange.getMessage().setBody(mapper.writer().writeValueAsString(rootNode));
            exchange.getMessage().setHeader(BedrockConstants.MODEL_CONTENT_TYPE, "application/json");
            exchange.getMessage().setHeader(BedrockConstants.MODEL_ACCEPT_CONTENT_TYPE, "application/json");
        });

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testInvokeMistralSmall2402Model() throws InterruptedException {

        result.expectedMessageCount(1);
        final Exchange result = template.send("direct:send_mistral_small_2402", exchange -> {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode rootNode = mapper.createObjectNode();
            rootNode.putIfAbsent("prompt",
                    new TextNode("\"<s>[INST] Can you tell the history of Mayflower? [/INST]\\\""));

            rootNode.putIfAbsent("max_tokens", new IntNode(300));
            rootNode.putIfAbsent("temperature", new DoubleNode(0.5));
            rootNode.putIfAbsent("top_p", new DoubleNode(0.9));
            rootNode.putIfAbsent("top_k", new IntNode(50));

            exchange.getMessage().setBody(mapper.writer().writeValueAsString(rootNode));
            exchange.getMessage().setHeader(BedrockConstants.MODEL_CONTENT_TYPE, "application/json");
            exchange.getMessage().setHeader(BedrockConstants.MODEL_ACCEPT_CONTENT_TYPE, "application/json");
        });

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testConverseWithClaudeModel() throws InterruptedException {
        result.expectedMessageCount(1);
        final Exchange result = template.send("direct:converse_claude", exchange -> {
            // Create a message using the Converse API
            java.util.List<software.amazon.awssdk.services.bedrockruntime.model.Message> messages = new java.util.ArrayList<>();
            messages.add(software.amazon.awssdk.services.bedrockruntime.model.Message.builder()
                    .role(software.amazon.awssdk.services.bedrockruntime.model.ConversationRole.USER)
                    .content(software.amazon.awssdk.services.bedrockruntime.model.ContentBlock
                            .fromText("What is the capital of France?"))
                    .build());

            exchange.getMessage().setHeader(BedrockConstants.CONVERSE_MESSAGES, messages);

            // Optional: Add inference configuration
            software.amazon.awssdk.services.bedrockruntime.model.InferenceConfiguration inferenceConfig
                    = software.amazon.awssdk.services.bedrockruntime.model.InferenceConfiguration.builder()
                            .maxTokens(100)
                            .temperature(0.7f)
                            .build();
            exchange.getMessage().setHeader(BedrockConstants.CONVERSE_INFERENCE_CONFIG, inferenceConfig);
        });

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testConverseStreamWithClaudeModel() throws InterruptedException {
        result.expectedMessageCount(1);
        final Exchange result = template.send("direct:converse_stream_claude", exchange -> {
            // Create a message using the Converse API
            java.util.List<software.amazon.awssdk.services.bedrockruntime.model.Message> messages = new java.util.ArrayList<>();
            messages.add(software.amazon.awssdk.services.bedrockruntime.model.Message.builder()
                    .role(software.amazon.awssdk.services.bedrockruntime.model.ConversationRole.USER)
                    .content(software.amazon.awssdk.services.bedrockruntime.model.ContentBlock
                            .fromText("Tell me a short joke about Java programming"))
                    .build());

            exchange.getMessage().setHeader(BedrockConstants.CONVERSE_MESSAGES, messages);
            exchange.getMessage().setHeader(BedrockConstants.STREAM_OUTPUT_MODE, "complete");

            // Optional: Add inference configuration
            software.amazon.awssdk.services.bedrockruntime.model.InferenceConfiguration inferenceConfig
                    = software.amazon.awssdk.services.bedrockruntime.model.InferenceConfiguration.builder()
                            .maxTokens(200)
                            .temperature(0.9f)
                            .build();
            exchange.getMessage().setHeader(BedrockConstants.CONVERSE_INFERENCE_CONFIG, inferenceConfig);
        });

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:send_titan_express")
                        .to("aws-bedrock:label?accessKey=RAW({{aws.manual.access.key}})&secretKey=RAW({{aws.manual.secret.key}})&region=eu-central-1&operation=invokeTextModel&modelId="
                            + BedrockModels.TITAN_TEXT_EXPRESS_V1.model)
                        .to(result);

                from("direct:send_titan_lite")
                        .to("aws-bedrock:label?accessKey=RAW({{aws.manual.access.key}})&secretKey=RAW({{aws.manual.secret.key}})&region=us-east-1&operation=invokeTextModel&modelId="
                            + BedrockModels.TITAN_TEXT_LITE_V1.model)
                        .to(result);

                from("direct:send_titan_image")
                        .to("aws-bedrock:label?accessKey=RAW({{aws.manual.access.key}})&secretKey=RAW({{aws.manual.secret.key}})&region=us-east-1&operation=invokeImageModel&modelId="
                            + BedrockModels.TITAN_IMAGE_GENERATOR_V1.model)
                        .split(body())
                        .unmarshal().base64()
                        .setHeader("CamelFileName", simple("image-${random(128)}.png")).to("file:target/generated_images")
                        .to(result);

                from("direct:send_titan_embeddings")
                        .to("aws-bedrock:label?accessKey=RAW({{aws.manual.access.key}})&secretKey=RAW({{aws.manual.secret.key}})&region=us-east-1&operation=invokeEmbeddingsModel&modelId="
                            + BedrockModels.TITAN_EMBEDDINGS_G1.model)
                        .to(result);

                from("direct:send_titan_multimodal_embeddings")
                        .to("aws-bedrock:label?accessKey=RAW({{aws.manual.access.key}})&secretKey=RAW({{aws.manual.secret.key}})&region=us-east-1&operation=invokeEmbeddingsModel&modelId="
                            + BedrockModels.TITAN_MULTIMODAL_EMBEDDINGS_G1.model)
                        .to(result);

                from("direct:send_titan_text_embeddings_v2")
                        .to("aws-bedrock:label?accessKey=RAW({{aws.manual.access.key}})&secretKey=RAW({{aws.manual.secret.key}})&region=us-east-1&operation=invokeEmbeddingsModel&modelId="
                            + BedrockModels.TITAN_TEXT_EMBEDDINGS_V2.model)
                        .to(result);

                from("direct:send_titan_premier")
                        .to("aws-bedrock:label?accessKey=RAW({{aws.manual.access.key}})&secretKey=RAW({{aws.manual.secret.key}})&region=us-east-1&operation=invokeTextModel&modelId="
                            + BedrockModels.TITAN_TEXT_PREMIER_V1.model)
                        .to(result);

                from("direct:send_jurassic2_model")
                        .to("aws-bedrock:label?accessKey=RAW({{aws.manual.access.key}})&secretKey=RAW({{aws.manual.secret.key}})&region=us-east-1&operation=invokeTextModel&modelId="
                            + BedrockModels.JURASSIC2_ULTRA.model)
                        .split(body())
                        .transform().jq(".data.text")
                        .log("Completions: ${body}")
                        .to(result);

                from("direct:send_jurassic2_mid_model")
                        .to("aws-bedrock:label?accessKey=RAW({{aws.manual.access.key}})&secretKey=RAW({{aws.manual.secret.key}})&region=us-east-1&operation=invokeTextModel&modelId="
                            + BedrockModels.JURASSIC2_MID.model)
                        .split(body())
                        .transform().jq(".data.text")
                        .log("Completions: ${body}")
                        .to(result);

                from("direct:send_anthropic_v1_model")
                        .to("aws-bedrock:label?accessKey=RAW({{aws.manual.access.key}})&secretKey=RAW({{aws.manual.secret.key}})&region=us-east-1&operation=invokeTextModel&modelId="
                            + BedrockModels.ANTROPHIC_CLAUDE_INSTANT_V1.model)
                        .log("${body}")
                        .to(result);

                from("direct:send_anthropic_v2_model")
                        .to("aws-bedrock:label?accessKey=RAW({{aws.manual.access.key}})&secretKey=RAW({{aws.manual.secret.key}})&region=us-east-1&operation=invokeTextModel&modelId="
                            + BedrockModels.ANTROPHIC_CLAUDE_V2.model)
                        .log("${body}")
                        .to(result);

                from("direct:send_anthropic_v21_model")
                        .to("aws-bedrock:label?accessKey=RAW({{aws.manual.access.key}})&secretKey=RAW({{aws.manual.secret.key}})&region=us-east-1&operation=invokeTextModel&modelId="
                            + BedrockModels.ANTROPHIC_CLAUDE_V2_1.model)
                        .log("${body}")
                        .to(result);

                from("direct:send_anthropic_v3_model")
                        .to("aws-bedrock:label?accessKey=RAW({{aws.manual.access.key}})&secretKey=RAW({{aws.manual.secret.key}})&region=us-east-1&operation=invokeTextModel&modelId="
                            + BedrockModels.ANTROPHIC_CLAUDE_V3.model)
                        .log("Completions: ${body}")
                        .to(result);

                from("direct:send_anthropic_v3_haiku_model")
                        .to("aws-bedrock:label?accessKey=RAW({{aws.manual.access.key}})&secretKey=RAW({{aws.manual.secret.key}})&region=us-east-1&operation=invokeTextModel&modelId="
                            + BedrockModels.ANTROPHIC_CLAUDE_HAIKU_V3.model)
                        .log("Completions: ${body}")
                        .to(result);

                from("direct:send_mistral_7b_instruct_model")
                        .to("aws-bedrock:label?accessKey=RAW({{aws.manual.access.key}})&secretKey=RAW({{aws.manual.secret.key}})&region=us-east-1&operation=invokeTextModel&modelId="
                            + BedrockModels.MISTRAL_7B_INSTRUCT.model)
                        .log("Completions: ${body}")
                        .to(result);

                from("direct:send_mistral_8x7b_instruct_model")
                        .to("aws-bedrock:label?accessKey=RAW({{aws.manual.access.key}})&secretKey=RAW({{aws.manual.secret.key}})&region=us-east-1&operation=invokeTextModel&modelId="
                            + BedrockModels.MISTRAL_8x7B_INSTRUCT.model)
                        .log("Completions: ${body}")
                        .to(result);

                from("direct:send_mistral_large_model")
                        .to("aws-bedrock:label?accessKey=RAW({{aws.manual.access.key}})&secretKey=RAW({{aws.manual.secret.key}})&region=us-east-1&operation=invokeTextModel&modelId="
                            + BedrockModels.MISTRAL_LARGE.model)
                        .log("Completions: ${body}")
                        .to(result);

                from("direct:send_nova_lite")
                        .to("aws-bedrock:label?accessKey=RAW({{aws.manual.access.key}})&secretKey=RAW({{aws.manual.secret.key}})&region=us-east-1&operation=invokeTextModel&modelId="
                            + BedrockModels.NOVA_LITE_V1.model)
                        .log("Completions: ${body}")
                        .to(result);

                from("direct:send_jamba_15_large")
                        .to("aws-bedrock:label?accessKey=RAW({{aws.manual.access.key}})&secretKey=RAW({{aws.manual.secret.key}})&region=us-east-1&operation=invokeTextModel&modelId="
                            + BedrockModels.JAMBA_1_5_LARGE.model)
                        .log("Completions: ${body}")
                        .to(result);

                from("direct:send_cohere_command_r")
                        .to("aws-bedrock:label?accessKey=RAW({{aws.manual.access.key}})&secretKey=RAW({{aws.manual.secret.key}})&region=us-east-1&operation=invokeTextModel&modelId="
                            + BedrockModels.COHERE_COMMAND_R.model)
                        .log("Completions: ${body}")
                        .to(result);

                from("direct:send_llama3_8b_instruct")
                        .to("aws-bedrock:label?accessKey=RAW({{aws.manual.access.key}})&secretKey=RAW({{aws.manual.secret.key}})&region=us-east-1&operation=invokeTextModel&modelId="
                            + BedrockModels.LLAMA3_8B_INSTRUCT.model)
                        .log("Completions: ${body}")
                        .to(result);

                from("direct:send_mistral_large_2407")
                        .to("aws-bedrock:label?accessKey=RAW({{aws.manual.access.key}})&secretKey=RAW({{aws.manual.secret.key}})&region=us-east-1&operation=invokeTextModel&modelId="
                            + BedrockModels.MISTRAL_LARGE_2407.model)
                        .log("Completions: ${body}")
                        .to(result);

                from("direct:send_nova_micro")
                        .to("aws-bedrock:label?accessKey=RAW({{aws.manual.access.key}})&secretKey=RAW({{aws.manual.secret.key}})&region=us-east-1&operation=invokeTextModel&modelId="
                            + BedrockModels.NOVA_MICRO_V1.model)
                        .log("Completions: ${body}")
                        .to(result);

                from("direct:send_nova_pro")
                        .to("aws-bedrock:label?accessKey=RAW({{aws.manual.access.key}})&secretKey=RAW({{aws.manual.secret.key}})&region=us-east-1&operation=invokeTextModel&modelId="
                            + BedrockModels.NOVA_PRO_V1.model)
                        .log("Completions: ${body}")
                        .to(result);

                from("direct:send_jamba_15_mini")
                        .to("aws-bedrock:label?accessKey=RAW({{aws.manual.access.key}})&secretKey=RAW({{aws.manual.secret.key}})&region=us-east-1&operation=invokeTextModel&modelId="
                            + BedrockModels.JAMBA_1_5_MINI.model)
                        .log("Completions: ${body}")
                        .to(result);

                from("direct:send_cohere_command_r_plus")
                        .to("aws-bedrock:label?accessKey=RAW({{aws.manual.access.key}})&secretKey=RAW({{aws.manual.secret.key}})&region=us-east-1&operation=invokeTextModel&modelId="
                            + BedrockModels.COHERE_COMMAND_R_PLUS.model)
                        .log("Completions: ${body}")
                        .to(result);

                from("direct:send_llama31_70b_instruct")
                        .to("aws-bedrock:label?accessKey=RAW({{aws.manual.access.key}})&secretKey=RAW({{aws.manual.secret.key}})&region=us-east-1&operation=invokeTextModel&modelId="
                            + BedrockModels.LLAMA3_1_70B_INSTRUCT.model)
                        .log("Completions: ${body}")
                        .to(result);

                from("direct:send_llama32_11b_instruct")
                        .to("aws-bedrock:label?accessKey=RAW({{aws.manual.access.key}})&secretKey=RAW({{aws.manual.secret.key}})&region=us-east-1&operation=invokeTextModel&modelId="
                            + BedrockModels.LLAMA3_2_11B_INSTRUCT.model)
                        .log("Completions: ${body}")
                        .to(result);

                from("direct:send_anthropic_claude_35_sonnet_2")
                        .to("aws-bedrock:label?accessKey=RAW({{aws.manual.access.key}})&secretKey=RAW({{aws.manual.secret.key}})&region=us-east-1&operation=invokeTextModel&modelId="
                            + BedrockModels.ANTROPHIC_CLAUDE_V35_2.model)
                        .log("Completions: ${body}")
                        .to(result);

                from("direct:send_anthropic_claude_35_haiku")
                        .to("aws-bedrock:label?accessKey=RAW({{aws.manual.access.key}})&secretKey=RAW({{aws.manual.secret.key}})&region=us-east-1&operation=invokeTextModel&modelId="
                            + BedrockModels.ANTROPHIC_CLAUDE_HAIKU_V35.model)
                        .log("Completions: ${body}")
                        .to(result);

                from("direct:send_mistral_small_2402")
                        .to("aws-bedrock:label?accessKey=RAW({{aws.manual.access.key}})&secretKey=RAW({{aws.manual.secret.key}})&region=us-east-1&operation=invokeTextModel&modelId="
                            + BedrockModels.MISTRAL_SMALL_2402.model)
                        .log("Completions: ${body}")
                        .to(result);

                from("direct:converse_claude")
                        .to("aws-bedrock:label?accessKey=RAW({{aws.manual.access.key}})&secretKey=RAW({{aws.manual.secret.key}})&region=us-east-1&operation=converse&modelId="
                            + BedrockModels.ANTROPHIC_CLAUDE_V3.model)
                        .log("Converse response: ${body}")
                        .to(result);

                from("direct:converse_stream_claude")
                        .to("aws-bedrock:label?accessKey=RAW({{aws.manual.access.key}})&secretKey=RAW({{aws.manual.secret.key}})&region=us-east-1&operation=converseStream&modelId="
                            + BedrockModels.ANTROPHIC_CLAUDE_V3.model)
                        .log("Converse stream response: ${body}")
                        .to(result);
            }
        };
    }
}
