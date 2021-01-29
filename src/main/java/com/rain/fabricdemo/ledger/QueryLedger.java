package com.rain.fabricdemo.ledger;

import org.hyperledger.fabric.gateway.Contract;
import org.hyperledger.fabric.gateway.Transaction;
import org.hyperledger.fabric.gateway.impl.GatewayImpl;
import org.hyperledger.fabric.sdk.Channel;
import org.hyperledger.fabric.sdk.Peer;
import org.hyperledger.fabric.sdk.ProposalResponse;
import org.hyperledger.fabric.sdk.QueryByChaincodeRequest;

import java.util.Collection;
import java.util.LinkedList;

public class QueryLedger {
    public static void queryInPeers(GatewayImpl gateway, Channel channel, Collection<Peer> endorserSet, String account) {
        try {

            QueryByChaincodeRequest queryByChaincodeRequest = gateway.getClient().newQueryProposalRequest();
            queryByChaincodeRequest.setChaincodeName("mycc");
            queryByChaincodeRequest.setFcn("query");
            queryByChaincodeRequest.setArgs(account);

            Collection<ProposalResponse> proposalResponses = channel.queryByChaincode(queryByChaincodeRequest, endorserSet);
            for (ProposalResponse prores: proposalResponses) {
                String result = prores.getProposalResponse().getResponse().getPayload().toStringUtf8();
                System.out.printf("%s: %s is %s\n", prores.getPeer().getName(), account, result);
            }
        } catch (Exception e) {
            System.out.println("queryByPeer Error");
            e.printStackTrace();
        }
    }

    public static void initInPeers(Contract contract, Collection<Peer> endorserSet, String account) {
        try {

            Transaction transaction = contract.createTransaction("Init");

            transaction.setEndorsingPeers(endorserSet);

            transaction.submit("WIN"+account, "100", "WIN"+account+"test", "100");
        } catch (Exception e) {
            System.out.println("Invoke Error");
            e.printStackTrace();

        }
    }
}
