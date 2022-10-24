# Camel casper producer component
Camel Casper component for interacting with Casper network using RPC calls (Java SDK)


* RPC Operations
---
## DEPLOY
```java
from("direct:start").to("casper://node_url?operation=deploy&deployHash=value")
```
Returns a Deploy object 
### Parameters
| Name | Type | Description | Mandatory |
|---|---|---|---|
| `deployHash` | `String` | Hex-encoded hash of a deploy | Yes |


## NETWORK_PEERS
```java
from("direct:start").to("casper://node_url?operation=network_peers")
```
Returns  the list of connected pairs

## NODE_STATUS
```java
from("direct:start").to("casper://node_url?operation=node_status")
```
Returns a NodeStatus object 

## LAST_BLOCK
```java
from("direct:start").to("casper://node_url?operation=last_block")
```
Returns the last minted block from the blockchain

## BLOCK
```java
from("direct:start").to("casper://node_url?operation=block?blockHash=value")
        
from("direct:start").to("casper://node_url?operation=block?blockHeight=value")
```
Returns a block from the blockchain using a block hash or a block height

### Parameters
Operation needs at least one of the two following parameters to be passed =  blockHash or blockHeight 

| Name | Type | Description | Mandatory |
|---|---|---|---|
| `blockHash` | `String` | Hex-encoded hash of a block | No |
| `blockHeight` | `String` | Hex-encoded hash of a block | No |

## LAST_BLOCK_TRANSFERS
```java
from("direct:start").to("casper://node_url?operation=last_block_transfers")

```
Returns the list of transfers within the last minted block


## BLOCK_TRANSFERS
```java
from("direct:start").to("casper://node_url?operation=last_block_transfers?blockHash=value")

from("direct:start").to("casper://node_url?operation=last_block_transfers?blockHeight=value")
```
Returns the list of transfers within a minted block

### Parameters
Operation needs at least one of the two following parameters to be passed =  blockHash or blockHeight

| Name | Type | Description | Mandatory |
|---|---|---|---|
| `blockHash` | `String` | Hex-encoded hash of a block | No |
| `blockHeight` | `String` | Hex-encoded hash of a block | No |

## STATE_ROOT_HASH
```java
from("direct:start").to("casper://node_url?operation=state_root_hash")

from("direct:start").to("casper://node_url?operation=state_root_hash?blockHash=value")
```
Returns  the state root hash String

### Parameters

| Name | Type | Description | Mandatory |
|---|---|---|---|
| `blockHash` | `String` | Hex-encoded hash of a block | No |


## ACCOUNT_INFO
```java
from("direct:start").to("casper://node_url?operation=account_info?publick_key=value0&blockHash=value1")

```
Returns  an Account object by a given block hash and account public key

### Parameters

| Name | Type | Description | Mandatory |
|---|---|---|---|
| `publick_key` | `String` | Hex-encoded hash of a Public key  | Yes |
| `blockHash` | `String` | Hex-encoded hash of a block  | Yes |


## AUCTION_INFO
```java
from("direct:start").to("casper://node_url?operation=auction_info?blockHash=value")
        
from("direct:start").to("casper://node_url?operation=auction_info?blockHeight=value")
```
returns an AuctionState object that contains the bids and validators

### Parameters

Operation needs at least one of the two following parameters to be passed =  blockHash or blockHeight

| Name | Type | Description | Mandatory |
|---|---|---|---|
| `blockHash` | `String` | Hex-encoded hash of a block | No |
| `blockHeight` | `String` | Hex-encoded hash of a block | No |



## ERA_INFO
```java
from("direct:start").to("casper://node_url?operation=era_info?blockHash=value")
        
from("direct:start").to("casper://node_url?operation=era_info?blockHeight=value")
```
returns  an EraSummary object

### Parameters

Operation needs at least one of the two following parameters to be passed =  blockHash or blockHeight

| Name | Type | Description | Mandatory |
|---|---|---|---|
| `blockHash` | `String` | Hex-encoded hash of a block | No |
| `blockHeight` | `String` | Hex-encoded hash of a block | No |

## STATE_ITEM
```java
from("direct:start").to("casper://node_url?operation=state_item?stateRootHash=value0&key=value1&path=value2")

```
returns  a StoredValue object

### Parameters

| Name | Type | Description | Mandatory |
|---|---|---|---|
| `stateRootHash` | `String` | Hex-encoded hash of the state root | Yes |
| `key` | `String` | casper_types::Key  formatted as a string | Yes |
| `path` | `String` | path components starting from the key as base, multivalued (values separated by ",") | No |

## ACCOUNT_BALANCE
```java
from("direct:start").to("casper://node_url?operation=account_balance?stateRootHash=value0&purseUref=value1")

```
returns  the balance (in motes) of an account

### Parameters

| Name | Type | Description | Mandatory |
|---|---|---|---|
| `stateRootHash` | `String` | Hex-encoded hash of the state root | Yes |
| `purseUref` | `String` | Hex-encoded hash of the balance URef object | Yes |

## PUT_DEPLOY
```java
//get a signed deploy
//Deploy deploy = Signed deploy ;
from("direct:start").
        .setHeader("DEPLOY", deploy)
        .to("casper://node_url?operation=put_deploy")

```
sends a deploy to the network and returns its hash if it succeeds.

## DICTIONNARY_ITEM
```java
from("direct:start").to("casper://node_url?operation=dictionnary_item?stateRootHash=value0&dictionnaryItemKey=value1&seedUref=value2")

```
returns an item from a Dictionary (StoredValue object)

### Parameters

| Name | Type | Description | Mandatory |
|---|---|---|---|
| `stateRootHash` | `String` | Hex-encoded hash of the state root | Yes |
| `dictionnaryItemKey` | `String` | dictionary item key formatted as a string | Yes |
| `seedUref` | `String` | dictionary's seed URef formatted as string | Yes |

## GLOBAL_STATE
```java
from("direct:start").to("casper://node_url?operation=global_state?stateRootHash=value")

from("direct:start").to("casper://node_url?operation=global_state?blockHash=value")

```
Sends a query to global state using either a Block hash or state root hash, returns a StoredValue object

### Parameters

Operation needs at least one of the two following parameters to be passed =  stateRootHash or blockHash

| Name | Type | Description | Mandatory |
|---|---|---|---|
| `blockHash` | `String` | Hex-encoded hash of a block | No |
| `stateRootHash` | `String` |  Hex-encoded hash of the state root  | No |


# Camel casper consumer component

The casper consumer component polls casper SSE store for the following events : 

* DEPLOY_PROCESSED : triggered when a deploy is processed

```java
from("casper://node_url?even=DEPLOY_PROCESSED").log("a deploy has been processed")

```


* DEPLOY_ACCEPTED : triggered when a deploy is accepted 

```java
from("casper://node_url?even=DEPLOY_ACCEPTED").log("a deploy has been accepted")

```

* DEPLOY_EXPIRED :  triggered when a deploy has expired

```java
from("casper://node_url?even=DEPLOY_EXPIRED").log("Your deploy has expired")

```

* BLOCK_ADDED   :   triggered when a block  has been minted


```java
from("casper://node_url?even=BLOCK_ADDED").log("Block added")

```

* FINALITY_SIGNATURE :  triggered when a deploy  has been signed


```java
from("casper://node_url?even=FINALITY_SIGNATURE").log("FINALITY_SIGNATURE")

```

* FAULT
* STEP
