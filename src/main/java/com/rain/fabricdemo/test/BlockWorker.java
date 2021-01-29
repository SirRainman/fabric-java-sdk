package com.rain.fabricdemo.test;

import org.apache.commons.codec.binary.Hex;
import org.hyperledger.fabric.gateway.*;
import org.hyperledger.fabric.protos.ledger.rwset.kvrwset.KvRwset;
import org.hyperledger.fabric.sdk.*;
import org.hyperledger.fabric.sdk.exception.CryptoException;
import org.hyperledger.fabric.sdk.exception.InvalidArgumentException;
import org.hyperledger.fabric.sdk.exception.ProposalException;
import org.hyperledger.fabric.sdk.security.CryptoSuite;
import static org.hyperledger.fabric.sdk.BlockInfo.EnvelopeType.TRANSACTION_ENVELOPE;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.InvalidKeyException;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import static java.nio.charset.StandardCharsets.UTF_8;


import static java.lang.String.format;

public class BlockWorker {
    // connection.json 里应该配置网络信息
    private static final Path NETWORK_CONFIG_PATH = Paths.get("src", "main", "resources", "connection-kubernetes.json");
    // 证书的位置
    private static final Path credentialPath = Paths.get("src", "main", "resources", "crypto-config",
            "peerOrganizations", "org1-example-com", "users", "Admin@org1-example-com", "msp");

    private static Path certificatePath;
    private static X509Certificate certificate;
    private static Path privateKeyPath;
    private static PrivateKey privateKey;
    private static Gateway.Builder builder;
    private static Network network;


    public static void blockWalker(int queryBlockHeight) throws Exception {
        HFClient client = HFClient.createNewInstance();
        client.setCryptoSuite(CryptoSuite.Factory.getCryptoSuite());
        Channel channel = network.getChannel();

        BlockInfo blockInfo = channel.queryBlockByNumber(queryBlockHeight);
        long blockNumber = blockInfo.getBlockNumber();

        out("current block number %d has data hash: %s", blockNumber, Hex.encodeHexString(blockInfo.getDataHash()));
        out("current block number %d has previous hash id: %s", blockNumber, Hex.encodeHexString(blockInfo.getPreviousHash()));
        out("current block number %d has calculated block hash is %s", blockNumber, Hex.encodeHexString(SDKUtils.calculateBlockHash(client, blockNumber, blockInfo.getPreviousHash(), blockInfo.getDataHash())));
        out("current block number %d has %d envelope count:", blockNumber, blockInfo.getEnvelopeCount());

        int i = 0, transactionCount = 0;
        for (BlockInfo.EnvelopeInfo envelopeInfo : blockInfo.getEnvelopeInfos()) {
            i++;
            out("  Transaction number %d has transaction id: %s", i, envelopeInfo.getTransactionID());
            final String channelId = envelopeInfo.getChannelId();

            out("  Transaction number %d has channel id: %s", i, channelId);
            out("  Transaction number %d has transaction timestamp: %tB %<te,  %<tY  %<tT %<Tp", i, envelopeInfo.getTimestamp());
            out("  Transaction number %d has type id: %s", i, envelopeInfo.getType());
            out("  Transaction number %d has nonce : %s", i, Hex.encodeHexString(envelopeInfo.getNonce()));
            out("  Transaction number %d has submitter mspid: %s,  certificate: %s", i, envelopeInfo.getCreator().getMspid(), envelopeInfo.getCreator().getId().length());

            if (envelopeInfo.getType() == TRANSACTION_ENVELOPE) {
                ++transactionCount;
                BlockInfo.TransactionEnvelopeInfo transactionEnvelopeInfo = (BlockInfo.TransactionEnvelopeInfo) envelopeInfo;

                out("  Transaction number %d has %d actions", i, transactionEnvelopeInfo.getTransactionActionInfoCount());
                out("  Transaction number %d isValid %b", i, transactionEnvelopeInfo.isValid());
                out("  Transaction number %d validation code %d", i, transactionEnvelopeInfo.getValidationCode());

                int j = 0;
                for (BlockInfo.TransactionEnvelopeInfo.TransactionActionInfo transactionActionInfo : transactionEnvelopeInfo.getTransactionActionInfos()) {
                    ++j;
                    out("   Transaction action %d has response status %d", j, transactionActionInfo.getResponseStatus());
                    out("   Transaction action %d has response message bytes as string: %s", j, printableString(new String(transactionActionInfo.getResponseMessageBytes(), UTF_8)));
                    out("   Transaction action %d has %d endorsements", j, transactionActionInfo.getEndorsementsCount());

                    for (int n = 0; n < transactionActionInfo.getEndorsementsCount(); ++n) {
                        BlockInfo.EndorserInfo endorserInfo = transactionActionInfo.getEndorsementInfo(n);
                        out("Endorser %d signature: %s", n, Hex.encodeHexString(endorserInfo.getSignature()));
                        out("Endorser %d endorser: mspid %s \n certificate %s", n, endorserInfo.getMspid(), endorserInfo.getId().length());
                    }
                    out("   Transaction action %d has %d chaincode input arguments", j, transactionActionInfo.getChaincodeInputArgsCount());
                    for (int z = 0; z < transactionActionInfo.getChaincodeInputArgsCount(); ++z) {
                        out("     Transaction action %d has chaincode input argument %d is: %s", j, z, printableString(new String(transactionActionInfo.getChaincodeInputArgs(z), UTF_8)));
                    }

                    out("   Transaction action %d proposal response status: %d", j, transactionActionInfo.getProposalResponseStatus());
                    out("   Transaction action %d proposal response payload: %s", j, printableString(new String(transactionActionInfo.getProposalResponsePayload())));

                    String chaincodeIDName = transactionActionInfo.getChaincodeIDName();
                    String chaincodeIDVersion = transactionActionInfo.getChaincodeIDVersion();
                    out("   Transaction action %d proposal chaincodeIDName: %s, chaincodeIDVersion: %s", j, chaincodeIDName, chaincodeIDVersion);


                    TxReadWriteSetInfo rwsetInfo = transactionActionInfo.getTxReadWriteSet();
                    if (null != rwsetInfo) {
                        out("   Transaction action %d has %d name space read write sets", j, rwsetInfo.getNsRwsetCount());
                    }
                }
            }
        }
    }

    public static void main(String[] args) throws Exception {

        certificatePath = credentialPath.resolve(Paths.get("signcerts", "Admin@org1-example-com-cert.pem"));
        certificate = readX509Certificate(certificatePath);
        privateKeyPath = credentialPath.resolve(Paths.get("keystore", "key.pem"));
        privateKey = getPrivateKey(privateKeyPath);

        // 加载一个钱包，里面有接入网络所需要的identities
        Wallet wallet = Wallets.newInMemoryWallet();
        // Path walletDir = Paths.get("wallet");
        // Wallet wallet = Wallets.newFileSystemWallet(walletDir);
        wallet.put("user", Identities.newX509Identity("Org1MSP", certificate, privateKey));

        // 设置连接网络所需要的gateway connection配置信息
        builder = Gateway.createBuilder().identity(wallet, "user").networkConfig(NETWORK_CONFIG_PATH);

        // 创建Gateway连接
        try (Gateway gateway = builder.connect()) {
            // 接入channel
            network = gateway.getNetwork("mychannel");

            blockWalker(9);
        } catch (Exception e) {
            System.out.println("Main Error!");
            e.printStackTrace();
        }
    }

    private static X509Certificate readX509Certificate(final Path certificatePath) throws IOException, CertificateException {
        try (Reader certificateReader = Files.newBufferedReader(certificatePath, StandardCharsets.UTF_8)) {
            return Identities.readX509Certificate(certificateReader);
        }
    }

    private static PrivateKey getPrivateKey(final Path privateKeyPath) throws IOException, InvalidKeyException {
        try (Reader privateKeyReader = Files.newBufferedReader(privateKeyPath, StandardCharsets.UTF_8)) {
            return Identities.readPrivateKey(privateKeyReader);
        }
    }

    static void out(String format, Object... args) {
        System.err.flush();
        System.out.flush();
        System.out.println(format(format, args));
        System.err.flush();
        System.out.flush();
    }

    static String printableString(final String string) {
        int maxLogStringLength = 64;
        if (string == null || string.length() == 0) {
            return string;
        }

        String ret = string.replaceAll("[^\\p{Print}]", "?");

        ret = ret.substring(0, Math.min(ret.length(), maxLogStringLength)) + (ret.length() > maxLogStringLength ? "..." : "");

        return ret;

    }
}
