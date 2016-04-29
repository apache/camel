/**
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
package org.apache.camel.component.quickfixj.examples.trading;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import quickfix.ConfigError;
import quickfix.DataDictionary;
import quickfix.DataDictionaryProvider;
import quickfix.FieldConvertError;
import quickfix.FieldNotFound;
import quickfix.FixVersions;
import quickfix.IncorrectTagValue;
import quickfix.LogUtil;
import quickfix.Message;
import quickfix.MessageUtils;
import quickfix.Session;
import quickfix.SessionID;
import quickfix.SessionNotFound;
import quickfix.UnsupportedMessageType;
import quickfix.field.ApplVerID;
import quickfix.field.AvgPx;
import quickfix.field.CumQty;
import quickfix.field.ExecID;
import quickfix.field.ExecTransType;
import quickfix.field.ExecType;
import quickfix.field.LastPx;
import quickfix.field.LastQty;
import quickfix.field.LastShares;
import quickfix.field.LeavesQty;
import quickfix.field.OrdStatus;
import quickfix.field.OrdType;
import quickfix.field.OrderID;
import quickfix.field.OrderQty;
import quickfix.field.Price;
import quickfix.field.Side;
import quickfix.field.Symbol;

/**
 * Trade executor based on QFJ example "executor" (No Camel dependencies)
 */
public class TradeExecutor {
    private static final Logger LOG = LoggerFactory.getLogger(TradeExecutor.class);
    
    private boolean alwaysFillLimitOrders;
    private Set<String> validOrderTypes = new HashSet<String>();
    private  MarketQuoteProvider marketQuoteProvider;

    private List<QuickfixjMessageListener> listeners = new CopyOnWriteArrayList<QuickfixjMessageListener>();
    
    private int orderID;
    private int execID;
    
    public TradeExecutor() throws ConfigError, FieldConvertError {
        setAlwaysFillLimitOrders(true);

        Set<String> validOrderTypes = new HashSet<String>();
        validOrderTypes.add(OrdType.LIMIT + "");
        validOrderTypes.add(OrdType.MARKET + "");
        setValidOrderTypes(validOrderTypes);

        setMarketQuoteProvider(new DefaultMarketQuoteProvider(10.00));
    }
    
    public void setAlwaysFillLimitOrders(boolean alwaysFillLimitOrders) {
        this.alwaysFillLimitOrders = alwaysFillLimitOrders;
    }
    
    public void setMarketQuoteProvider(MarketQuoteProvider marketQuoteProvider) {
        this.marketQuoteProvider = marketQuoteProvider;
    }
    
    public void setValidOrderTypes(String validOrderTypes) {
        setValidOrderTypes(new HashSet<String>(Arrays.asList(validOrderTypes.split("\\s*,\\s*"))));
    }
    
    public void setValidOrderTypes(Set<String> validOrderTypes) {
        this.validOrderTypes = validOrderTypes;
    }
    
    public void addListener(QuickfixjMessageListener listener) {
        listeners.add(listener);
    }
    
    public void removeListener(QuickfixjMessageListener listener) {
        listeners.remove(listener);
    }
    
    public void execute(final Message message) throws FieldNotFound, UnsupportedMessageType, IncorrectTagValue {
        final SessionID sessionID = MessageUtils.getSessionID(message);

        try {
            if (message instanceof quickfix.fix40.NewOrderSingle) {
                onMessage((quickfix.fix40.NewOrderSingle) message, sessionID);
            } else if (message instanceof quickfix.fix41.NewOrderSingle) {
                onMessage((quickfix.fix41.NewOrderSingle) message, sessionID);
            } else if (message instanceof quickfix.fix42.NewOrderSingle) {
                onMessage((quickfix.fix42.NewOrderSingle) message, sessionID);
            } else if (message instanceof quickfix.fix43.NewOrderSingle) {
                onMessage((quickfix.fix43.NewOrderSingle) message, sessionID);
            } else if (message instanceof quickfix.fix44.NewOrderSingle) {
                onMessage((quickfix.fix44.NewOrderSingle) message, sessionID);
            } else if (message instanceof quickfix.fix50.NewOrderSingle) {
                onMessage((quickfix.fix50.NewOrderSingle) message, sessionID);
            }
        } catch (Exception e) {
            LOG.error("Error submitting execution task", e);
        }
    }
    
    private void onMessage(quickfix.fix40.NewOrderSingle order, SessionID sessionID) throws FieldNotFound, UnsupportedMessageType, IncorrectTagValue {
        try {
            validateOrder(order);

            OrderQty orderQty = order.getOrderQty();

            Price price = getPrice(order);

            quickfix.fix40.ExecutionReport accept = new quickfix.fix40.ExecutionReport(genOrderID(), genExecID(),
                    new ExecTransType(ExecTransType.NEW), new OrdStatus(OrdStatus.NEW), order.getSymbol(), order.getSide(),
                    orderQty, new LastShares(0), new LastPx(0), new CumQty(0), new AvgPx(0));

            accept.set(order.getClOrdID());
            sendMessage(sessionID, accept);

            if (isOrderExecutable(order, price)) {
                quickfix.fix40.ExecutionReport fill = new quickfix.fix40.ExecutionReport(genOrderID(), genExecID(),
                        new ExecTransType(ExecTransType.NEW), new OrdStatus(OrdStatus.FILLED), order.getSymbol(), order
                                .getSide(), orderQty, new LastShares(orderQty.getValue()), new LastPx(price.getValue()),
                        new CumQty(orderQty.getValue()), new AvgPx(price.getValue()));

                fill.set(order.getClOrdID());

                sendMessage(sessionID, fill);
            }
        } catch (RuntimeException e) {
            LogUtil.logThrowable(sessionID, e.getMessage(), e);
        }
    }

    private boolean isOrderExecutable(Message order, Price price) throws FieldNotFound {
        if (order.getChar(OrdType.FIELD) == OrdType.LIMIT) {
            BigDecimal limitPrice = new BigDecimal(order.getString(Price.FIELD));
            char side = order.getChar(Side.FIELD);
            BigDecimal thePrice = new BigDecimal(price.getValue());

            return (side == Side.BUY && thePrice.compareTo(limitPrice) <= 0)
                || ((side == Side.SELL || side == Side.SELL_SHORT) && thePrice.compareTo(limitPrice) >= 0);
        }
        return true;
    }

    private Price getPrice(Message message) throws FieldNotFound {
        Price price;
        if (message.getChar(OrdType.FIELD) == OrdType.LIMIT && alwaysFillLimitOrders) {
            price = new Price(message.getDouble(Price.FIELD));
        } else {
            if (marketQuoteProvider == null) {
                throw new RuntimeException("No market data provider specified for market order");
            }
            char side = message.getChar(Side.FIELD);
            if (side == Side.BUY) {
                price = new Price(marketQuoteProvider.getAsk(message.getString(Symbol.FIELD)));
            } else if (side == Side.SELL || side == Side.SELL_SHORT) {
                price = new Price(marketQuoteProvider.getBid(message.getString(Symbol.FIELD)));
            } else {
                throw new RuntimeException("Invalid order side: " + side);
            }
        }
        return price;
    }

    private void sendMessage(SessionID sessionID, Message message) {
        try {
            Session session = Session.lookupSession(sessionID);
            if (session == null) {
                throw new SessionNotFound(sessionID.toString());
            }
            
            DataDictionaryProvider provider = session.getDataDictionaryProvider();
            if (provider != null) {
                try {
                    ApplVerID applVerID = getApplVerID(session, message);
                    DataDictionary appDataDictionary = provider.getApplicationDataDictionary(applVerID);
                    appDataDictionary.validate(message, true);
                } catch (Exception e) {
                    LogUtil.logThrowable(sessionID, "Outgoing message failed validation: "
                            + e.getMessage(), e);
                    return;
                }
            }
            
            for (QuickfixjMessageListener listener : listeners) {
                try {
                    listener.onMessage(sessionID, message);
                } catch (Throwable e) {
                    LogUtil.logThrowable(sessionID, "Error while dispatching message", e);
                }
            }
            
        } catch (SessionNotFound e) {
            LOG.error(e.getMessage(), e);
        }
    }

    private ApplVerID getApplVerID(Session session, Message message) {
        String beginString = session.getSessionID().getBeginString();
        if (FixVersions.BEGINSTRING_FIXT11.equals(beginString)) {
            return new ApplVerID(ApplVerID.FIX50);
        } else {
            return MessageUtils.toApplVerID(beginString);
        }
    }

    private void onMessage(quickfix.fix41.NewOrderSingle order, SessionID sessionID) throws FieldNotFound, UnsupportedMessageType, IncorrectTagValue {
        try {
            validateOrder(order);
    
            OrderQty orderQty = order.getOrderQty();
            Price price = getPrice(order);
    
            quickfix.fix41.ExecutionReport accept = new quickfix.fix41.ExecutionReport(genOrderID(), genExecID(),
                    new ExecTransType(ExecTransType.NEW), new ExecType(ExecType.NEW), new OrdStatus(OrdStatus.NEW), order
                            .getSymbol(), order.getSide(), orderQty, new LastShares(0), new LastPx(0), new LeavesQty(0),
                    new CumQty(0), new AvgPx(0));
    
            accept.set(order.getClOrdID());
            sendMessage(sessionID, accept);
    
            if (isOrderExecutable(order, price)) {
                quickfix.fix41.ExecutionReport executionReport = new quickfix.fix41.ExecutionReport(genOrderID(),
                        genExecID(), new ExecTransType(ExecTransType.NEW), new ExecType(ExecType.FILL), new OrdStatus(
                                OrdStatus.FILLED), order.getSymbol(), order.getSide(), orderQty, new LastShares(orderQty
                                .getValue()), new LastPx(price.getValue()), new LeavesQty(0), new CumQty(orderQty
                                .getValue()), new AvgPx(price.getValue()));
    
                executionReport.set(order.getClOrdID());
    
                sendMessage(sessionID, executionReport);
            }
        } catch (RuntimeException e) {
            LogUtil.logThrowable(sessionID, e.getMessage(), e);
        }
    }

    private void onMessage(quickfix.fix42.NewOrderSingle order, SessionID sessionID) throws FieldNotFound, UnsupportedMessageType, IncorrectTagValue {
        try {
            validateOrder(order);
    
            OrderQty orderQty = order.getOrderQty();
            Price price = getPrice(order);
    
            quickfix.fix42.ExecutionReport accept = new quickfix.fix42.ExecutionReport(genOrderID(), genExecID(),
                    new ExecTransType(ExecTransType.NEW), new ExecType(ExecType.NEW), new OrdStatus(OrdStatus.NEW), order
                            .getSymbol(), order.getSide(), new LeavesQty(0), new CumQty(0), new AvgPx(0));
    
            accept.set(order.getClOrdID());
            sendMessage(sessionID, accept);
    
            if (isOrderExecutable(order, price)) {
                quickfix.fix42.ExecutionReport executionReport = new quickfix.fix42.ExecutionReport(genOrderID(),
                    genExecID(), new ExecTransType(ExecTransType.NEW), new ExecType(ExecType.FILL), 
                    new OrdStatus(OrdStatus.FILLED), order.getSymbol(), order.getSide(), new LeavesQty(0), 
                    new CumQty(orderQty.getValue()), new AvgPx(price.getValue()));
    
                executionReport.set(order.getClOrdID());
                executionReport.set(orderQty);
                executionReport.set(new LastShares(orderQty.getValue()));
                executionReport.set(new LastPx(price.getValue()));
    
                sendMessage(sessionID, executionReport);
            }
        } catch (RuntimeException e) {
            LogUtil.logThrowable(sessionID, e.getMessage(), e);
        }
    }

    private void validateOrder(Message order) throws IncorrectTagValue, FieldNotFound {
        OrdType ordType = new OrdType(order.getChar(OrdType.FIELD));
        if (!validOrderTypes.contains(Character.toString(ordType.getValue()))) {
            LOG.error("Order type not in ValidOrderTypes setting");
            throw new IncorrectTagValue(ordType.getField());
        }
        if (ordType.getValue() == OrdType.MARKET && marketQuoteProvider == null) {
            LOG.error("DefaultMarketPrice setting not specified for market order");
            throw new IncorrectTagValue(ordType.getField());
        }
    }

    private void onMessage(quickfix.fix43.NewOrderSingle order, SessionID sessionID) throws FieldNotFound, UnsupportedMessageType, IncorrectTagValue {
        try {
            validateOrder(order);
    
            OrderQty orderQty = order.getOrderQty();
            Price price = getPrice(order);
    
            quickfix.fix43.ExecutionReport accept = new quickfix.fix43.ExecutionReport(
                genOrderID(), genExecID(), new ExecType(ExecType.NEW), new OrdStatus(OrdStatus.NEW), 
                order.getSide(), new LeavesQty(order.getOrderQty().getValue()), new CumQty(0), new AvgPx(0));
    
            accept.set(order.getClOrdID());
            accept.set(order.getSymbol());
            sendMessage(sessionID, accept);
    
            if (isOrderExecutable(order, price)) {
                quickfix.fix43.ExecutionReport executionReport = new quickfix.fix43.ExecutionReport(genOrderID(),
                    genExecID(), new ExecType(ExecType.FILL), new OrdStatus(OrdStatus.FILLED), order.getSide(),
                    new LeavesQty(0), new CumQty(orderQty.getValue()), new AvgPx(price.getValue()));
    
                executionReport.set(order.getClOrdID());
                executionReport.set(order.getSymbol());
                executionReport.set(orderQty);
                executionReport.set(new LastQty(orderQty.getValue()));
                executionReport.set(new LastPx(price.getValue()));
    
                sendMessage(sessionID, executionReport);
            }
        } catch (RuntimeException e) {
            LogUtil.logThrowable(sessionID, e.getMessage(), e);
        }
    }

    private void onMessage(quickfix.fix44.NewOrderSingle order, SessionID sessionID) throws FieldNotFound, UnsupportedMessageType, IncorrectTagValue {
        try {
            validateOrder(order);
    
            OrderQty orderQty = order.getOrderQty();
            Price price = getPrice(order);
    
            quickfix.fix44.ExecutionReport accept = new quickfix.fix44.ExecutionReport(
                genOrderID(), genExecID(), new ExecType(ExecType.NEW), new OrdStatus(OrdStatus.NEW), 
                order.getSide(), new LeavesQty(order.getOrderQty().getValue()), new CumQty(0), new AvgPx(0));
    
            accept.set(order.getClOrdID());
            accept.set(order.getSymbol());
            sendMessage(sessionID, accept);
    
            if (isOrderExecutable(order, price)) {
                quickfix.fix44.ExecutionReport executionReport = new quickfix.fix44.ExecutionReport(genOrderID(),
                    genExecID(), new ExecType(ExecType.FILL), new OrdStatus(OrdStatus.FILLED), order.getSide(),
                    new LeavesQty(0), new CumQty(orderQty.getValue()), new AvgPx(price.getValue()));
    
                executionReport.set(order.getClOrdID());
                executionReport.set(order.getSymbol());
                executionReport.set(orderQty);
                executionReport.set(new LastQty(orderQty.getValue()));
                executionReport.set(new LastPx(price.getValue()));
    
                sendMessage(sessionID, executionReport);
            }
        } catch (RuntimeException e) {
            LogUtil.logThrowable(sessionID, e.getMessage(), e);
        }
    }

    private void onMessage(quickfix.fix50.NewOrderSingle order, SessionID sessionID) throws FieldNotFound, UnsupportedMessageType, IncorrectTagValue {
        try {
            validateOrder(order);

            OrderQty orderQty = order.getOrderQty();
            Price price = getPrice(order);

            quickfix.fix50.ExecutionReport accept = new quickfix.fix50.ExecutionReport(
                genOrderID(), genExecID(), new ExecType(ExecType.NEW), new OrdStatus(OrdStatus.NEW), 
                order.getSide(), new LeavesQty(order.getOrderQty().getValue()), new CumQty(0));

            accept.set(order.getClOrdID());
            accept.set(order.getSymbol());
            sendMessage(sessionID, accept);

            if (isOrderExecutable(order, price)) {
                quickfix.fix50.ExecutionReport executionReport = new quickfix.fix50.ExecutionReport(
                    genOrderID(), genExecID(), new ExecType(ExecType.FILL), new OrdStatus(OrdStatus.FILLED), 
                    order.getSide(), new LeavesQty(0), new CumQty(orderQty.getValue()));

                executionReport.set(order.getClOrdID());
                executionReport.set(order.getSymbol());
                executionReport.set(orderQty);
                executionReport.set(new LastQty(orderQty.getValue()));
                executionReport.set(new LastPx(price.getValue()));
                executionReport.set(new AvgPx(price.getValue()));
                
                sendMessage(sessionID, executionReport);
            }
        } catch (RuntimeException e) {
            LogUtil.logThrowable(sessionID, e.getMessage(), e);
        }
    }
    
    public OrderID genOrderID() {
        return new OrderID(Integer.valueOf(++orderID).toString());
    }

    public ExecID genExecID() {
        return new ExecID(Integer.valueOf(++execID).toString());
    }

    private static class DefaultMarketQuoteProvider implements MarketQuoteProvider {
        private double defaultMarketPrice;
        
        DefaultMarketQuoteProvider(double defaultMarketPrice) {
            this.defaultMarketPrice = defaultMarketPrice;
        }

        @Override
        public double getAsk(String symbol) {
            return defaultMarketPrice;
        }

        @Override
        public double getBid(String symbol) {
            return defaultMarketPrice;
        }
    };
}