package org.apache.camel.component.as2.api;

interface Constants {
    static final String SHA_1_AS2_ALGORITHM_NAME = "sha1";
    static final String SHA_1_JDK_ALGORITHM_NAME = "SHA-1";

    static final String MD5_AS2_ALGORITHM_NAME = "md5";
    static final String MD5_JDK_ALGORITHM_NAME = "MD5";
}

public enum AS2MicAlgorithm {
    SHA_1(Constants.SHA_1_JDK_ALGORITHM_NAME, Constants.SHA_1_AS2_ALGORITHM_NAME),
    MD5(Constants.MD5_JDK_ALGORITHM_NAME, Constants.MD5_AS2_ALGORITHM_NAME);
    

    

    public static String getJdkAlgorithmName(String as2AlgorithmName) {
        switch(as2AlgorithmName) {
        case Constants.SHA_1_AS2_ALGORITHM_NAME:
            return Constants.SHA_1_JDK_ALGORITHM_NAME;
        case Constants.MD5_AS2_ALGORITHM_NAME:
            return Constants.MD5_JDK_ALGORITHM_NAME;
        default:
            return null;
        }
    }
    
    private String jdkAlgorithmName;
    private String as2AlgorithmName;
    
    private AS2MicAlgorithm(String jdkAlgorithmName, String as2AlgorithmName) {
        this.jdkAlgorithmName = jdkAlgorithmName;
        this.as2AlgorithmName = as2AlgorithmName;
    }

    public String getJdkAlgorithmName() {
        return jdkAlgorithmName;
    }

    public String getAs2AlgorithmName() {
        return as2AlgorithmName;
    }

}
