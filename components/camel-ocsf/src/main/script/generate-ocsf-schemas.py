#!/usr/bin/env python3
#
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

"""
OCSF JSON Schema Generator for Apache Camel

This script generates JSON Schema files from the official OCSF schema that are
compatible with the jsonschema2pojo Maven plugin.

Key features:
- Generates individual .json schema files for each class and object
- Uses file-based $ref references (e.g., "$ref": "Attack.json")
- Includes javaType annotations for proper class naming
- Uses JSON Schema Draft-07 format

Usage:
    pip install ocsf-json-schema
    python generate-ocsf-schemas.py --version 1.7.0 --output ../resources/schema

The generated schemas are used by the jsonschema2pojo Maven plugin to generate
Java POJOs at build time.
"""

import argparse
import json
import sys
from pathlib import Path

try:
    from ocsf_json_schema import OcsfJsonSchemaEmbedded, get_ocsf_schema
except ImportError:
    print("Error: ocsf-json-schema package not found.")
    print("Install it with: pip install ocsf-json-schema")
    sys.exit(1)


# Java package for generated classes
JAVA_PACKAGE = "org.apache.camel.dataformat.ocsf.model"

# Classes to generate - main OCSF event classes
CLASSES_TO_GENERATE = [
    # Findings (Category 2)
    'detection_finding',
    'security_finding',
    'vulnerability_finding',
    'compliance_finding',
    'incident_finding',

    # System Activity (Category 1)
    'file_activity',
    'process_activity',
    'kernel_extension_activity',
    'kernel_activity',
    'memory_activity',
    'module_activity',
    'scheduled_job_activity',

    # Network Activity (Category 4)
    'network_activity',
    'http_activity',
    'dns_activity',
    'dhcp_activity',
    'rdp_activity',
    'smb_activity',
    'ssh_activity',
    'ftp_activity',
    'email_activity',
    'network_file_activity',
    'ntp_activity',

    # IAM (Category 3)
    'authentication',
    'authorize_session',
    'account_change',
    'group_management',
    'user_access',
    'entity_management',

    # Discovery (Category 5)
    'scan_activity',

    # Application Activity (Category 6)
    'web_resource_activity',
    'web_resources_activity',
    'api_activity',
    'datastore_activity',

    # Remediation (Category 7)
    'remediation_activity',
]

# Objects to generate - reusable OCSF objects
# These are objects that exist in the OCSF catalog (verified in 1.7.0)
OBJECTS_TO_GENERATE = [
    'account',
    'actor',
    'agent',
    'analytic',
    'api',
    'attack',
    'auth_factor',
    'authorization',
    'certificate',
    'cloud',
    'compliance',
    'container',
    'cve',
    'cvss',
    'cwe',
    'device',
    'display',
    'dns_query',
    'endpoint',
    'enrichment',
    'epss',
    'extension',
    'feature',
    'file',
    'finding_info',
    'fingerprint',
    'group',
    'http_header',
    'http_request',
    'http_response',
    'idp',
    'image',
    'ja4_fingerprint',
    'kb_article',
    'kill_chain_phase',
    'location',
    'logger',
    'malware',
    'metadata',
    'module',
    'network_endpoint',
    'network_interface',
    'observable',
    'organization',
    'os',
    'package',
    'policy',
    'process',
    'product',
    'remediation',
    'reputation',
    'request',
    'resource_details',
    'response',
    'rule',
    'san',
    'scan',
    'service',
    'session',
    'sub_technique',
    'tactic',
    'technique',
    'ticket',
    'tls',
    'url',
    'user',
    'vendor_attributes',
    'vulnerability',
]


def to_pascal_case(name: str) -> str:
    """Convert snake_case to PascalCase for Java class names."""
    return ''.join(word.capitalize() for word in name.split('_'))


def to_file_ref(object_name: str) -> str:
    """Convert object name to file reference."""
    return f"{to_pascal_case(object_name)}.json"


# Map property names to their corresponding object types
# This helps convert inline object definitions to $ref references
# Only include objects that actually exist in the OCSF catalog
PROPERTY_TO_OBJECT_MAP = {
    # Common property names that should reference specific objects
    'account': 'account',
    'actor': 'actor',
    'agent': 'agent',
    'agent_list': 'agent',  # array
    'analytic': 'analytic',
    'related_analytics': 'analytic',  # array
    'api': 'api',
    'attack': 'attack',
    'attacks': 'attack',  # array of attacks
    'auth_factor': 'auth_factor',
    'auth_factors': 'auth_factor',  # array
    'authorization': 'authorization',
    'certificate': 'certificate',
    'cloud': 'cloud',
    'compliance': 'compliance',
    'container': 'container',
    'cve': 'cve',
    'cvss': 'cvss',
    'cwe': 'cwe',
    'device': 'device',
    'display': 'display',
    'dns_query': 'dns_query',
    'endpoint': 'endpoint',
    'enrichment': 'enrichment',
    'enrichments': 'enrichment',  # array
    'epss': 'epss',
    'extension': 'extension',
    'feature': 'feature',
    'file': 'file',
    'finding_info': 'finding_info',
    'fingerprint': 'fingerprint',
    'group': 'group',
    'http_header': 'http_header',
    'http_request': 'http_request',
    'http_response': 'http_response',
    'idp': 'idp',
    'image': 'image',
    'ja4_fingerprint': 'ja4_fingerprint',
    'kb_article': 'kb_article',
    'kb_articles': 'kb_article',  # array
    'kb_article_list': 'kb_article',  # array
    'kill_chain': 'kill_chain_phase',  # array
    'kill_chain_phase': 'kill_chain_phase',
    'location': 'location',
    'logger': 'logger',
    'loggers': 'logger',  # array
    'malware': 'malware',
    'metadata': 'metadata',
    'module': 'module',
    'network_endpoint': 'network_endpoint',
    'network_interface': 'network_interface',
    'network_interfaces': 'network_interface',  # array
    'observable': 'observable',
    'observables': 'observable',  # array
    'organization': 'organization',
    'os': 'os',
    'package': 'package',
    'packages': 'package',  # array
    'policy': 'policy',
    'policies': 'policy',  # array
    'process': 'process',
    'parent_process': 'process',
    'product': 'product',
    'remediation': 'remediation',
    'reputation': 'reputation',
    'request': 'request',
    'resource': 'resource_details',
    'resources': 'resource_details',  # array
    'response': 'response',
    'rule': 'rule',
    'san': 'san',
    'scan': 'scan',
    'service': 'service',
    'session': 'session',
    'sub_technique': 'sub_technique',
    'tactic': 'tactic',
    'tactics': 'tactic',  # array
    'technique': 'technique',
    'ticket': 'ticket',
    'tls': 'tls',
    'url': 'url',
    'user': 'user',
    'users': 'user',  # array
    'vendor_attributes': 'vendor_attributes',
    'vulnerability': 'vulnerability',
    'vulnerabilities': 'vulnerability',  # array
    # Endpoint aliases
    'src_endpoint': 'network_endpoint',
    'dst_endpoint': 'network_endpoint',
    'proxy_endpoint': 'network_endpoint',
    # Process aliases
    'logon_process': 'process',
    'target_process': 'process',
    'child_process': 'process',
    # Hash/fingerprint aliases
    'raw_data_hash': 'fingerprint',
    'file_hash': 'fingerprint',
}


def extract_property_type(prop_schema: dict, all_objects: set, prop_name: str = '') -> dict:
    """
    Convert a property schema to use file-based $ref.
    Returns simplified property schema for jsonschema2pojo.
    """
    result = {}

    # Handle $ref to internal definitions
    if '$ref' in prop_schema:
        ref = prop_schema['$ref']
        if ref.startswith('#/$defs/') or ref.startswith('#/definitions/'):
            # Extract the definition name and convert to file reference
            def_name = ref.split('/')[-1]
            pascal_name = to_pascal_case(def_name)
            if def_name in all_objects or def_name.lower().replace('_', '') in [o.lower().replace('_', '') for o in all_objects]:
                return {"$ref": f"{pascal_name}.json"}
            else:
                # Unknown reference, keep as object
                return {"type": "object", "additionalProperties": True}
        else:
            return prop_schema

    # Check if this property name maps to a known object type
    # This converts inline object definitions to $ref references
    prop_name_lower = prop_name.lower()
    if prop_name_lower in PROPERTY_TO_OBJECT_MAP:
        object_name = PROPERTY_TO_OBJECT_MAP[prop_name_lower]
        if object_name in all_objects:
            pascal_name = to_pascal_case(object_name)
            # If the schema type is array, wrap the ref in items
            if prop_schema.get('type') == 'array':
                return {
                    'type': 'array',
                    'description': prop_schema.get('description', ''),
                    'items': {"$ref": f"{pascal_name}.json"}
                }
            # If it's an object type or has properties, use $ref
            elif prop_schema.get('type') == 'object' or 'properties' in prop_schema:
                return {"$ref": f"{pascal_name}.json"}

    # Copy basic properties
    if 'type' in prop_schema:
        result['type'] = prop_schema['type']

    # Use Long for timestamp fields (epoch milliseconds exceed Integer.MAX_VALUE)
    # This includes fields ending with _time and the 'time' field itself
    # Note: jsonschema2pojo uses 'existingJavaType' for primitive type overrides
    if prop_schema.get('type') == 'integer':
        is_timestamp_field = (
            prop_name.endswith('_time') or
            prop_name == 'time' or
            prop_name == 'duration'
        )
        if is_timestamp_field:
            result['existingJavaType'] = 'java.lang.Long'

    if 'description' in prop_schema:
        result['description'] = prop_schema['description']

    if 'title' in prop_schema:
        result['title'] = prop_schema['title']

    if 'format' in prop_schema:
        result['format'] = prop_schema['format']

    # Skip enum for integer ID fields to avoid generating poor enum names
    # (e.g., _0, _1, _2). These fields should just be integers.
    if 'enum' in prop_schema:
        # Only include enum if it's not an integer type or not an ID field
        is_integer_type = prop_schema.get('type') == 'integer'
        is_id_field = prop_name.endswith('_id')
        if not (is_integer_type and is_id_field):
            result['enum'] = prop_schema['enum']

    if 'pattern' in prop_schema:
        result['pattern'] = prop_schema['pattern']

    if 'minimum' in prop_schema:
        result['minimum'] = prop_schema['minimum']

    if 'maximum' in prop_schema:
        result['maximum'] = prop_schema['maximum']

    # Handle arrays
    if prop_schema.get('type') == 'array' and 'items' in prop_schema:
        result['items'] = extract_property_type(prop_schema['items'], all_objects, prop_name)

    # Handle nested objects with properties
    if 'properties' in prop_schema:
        result['properties'] = {}
        for nested_prop_name, prop_value in prop_schema['properties'].items():
            result['properties'][nested_prop_name] = extract_property_type(prop_value, all_objects, nested_prop_name)
        result['additionalProperties'] = prop_schema.get('additionalProperties', True)

    return result if result else {"type": "object", "additionalProperties": True}


def generate_object_schema(ocsf: OcsfJsonSchemaEmbedded, object_name: str,
                          output_dir: Path, ocsf_version: str, all_objects: set) -> bool:
    """Generate a JSON Schema file for an OCSF object."""
    try:
        schema = ocsf.get_object_schema(object_name=object_name)
        pascal_name = to_pascal_case(object_name)

        # Build simplified schema
        result = {
            "$schema": "http://json-schema.org/draft-07/schema#",
            "$id": f"https://schema.ocsf.io/{ocsf_version}/objects/{object_name}",
            "title": pascal_name,
            "type": "object",
            "javaType": f"{JAVA_PACKAGE}.{pascal_name}",
            "additionalProperties": True,
            "properties": {}
        }

        # Extract description
        if 'description' in schema:
            result['description'] = schema['description']
        elif 'title' in schema:
            result['description'] = f"The {schema['title']} object."

        # Process properties
        if 'properties' in schema:
            for prop_name, prop_schema in schema['properties'].items():
                result['properties'][prop_name] = extract_property_type(prop_schema, all_objects, prop_name)

        # Write to file
        output_file = output_dir / f"{pascal_name}.json"
        with open(output_file, 'w') as f:
            json.dump(result, f, indent=2)

        return True

    except Exception as e:
        print(f"    Warning: Could not generate object {object_name}: {e}")
        return False


def generate_class_schema(ocsf: OcsfJsonSchemaEmbedded, class_name: str,
                         output_dir: Path, ocsf_version: str, all_objects: set,
                         extends_base: bool = True) -> bool:
    """Generate a JSON Schema file for an OCSF event class."""
    try:
        schema = ocsf.get_class_schema(class_name=class_name, profiles=[])
        pascal_name = to_pascal_case(class_name)

        # Build schema structure
        result = {
            "$schema": "http://json-schema.org/draft-07/schema#",
            "$id": f"https://schema.ocsf.io/{ocsf_version}/classes/{class_name}",
            "title": pascal_name,
            "type": "object",
            "javaType": f"{JAVA_PACKAGE}.{pascal_name}",
            "additionalProperties": True,
        }

        # Add description
        if 'description' in schema:
            result['description'] = schema['description']
        else:
            result['description'] = f"{pascal_name} event class."

        # Extend base event
        if extends_base:
            result['allOf'] = [{"$ref": "OcsfEvent.json"}]

        # Process properties (excluding base event properties)
        result['properties'] = {}
        base_event_props = {
            'activity_id', 'activity_name', 'category_name', 'category_uid',
            'class_name', 'class_uid', 'count', 'duration', 'end_time', 'end_time_dt',
            'message', 'raw_data', 'severity', 'severity_id', 'start_time', 'start_time_dt',
            'status', 'status_code', 'status_detail', 'status_id', 'time', 'time_dt',
            'timezone_offset', 'type_name', 'type_uid', 'version', 'metadata',
            'observables', 'unmapped'
        }

        if 'properties' in schema:
            for prop_name, prop_schema in schema['properties'].items():
                # Skip base event properties if extending
                if extends_base and prop_name in base_event_props:
                    continue
                result['properties'][prop_name] = extract_property_type(prop_schema, all_objects, prop_name)

        # Write to file
        output_file = output_dir / f"{pascal_name}.json"
        with open(output_file, 'w') as f:
            json.dump(result, f, indent=2)

        return True

    except Exception as e:
        print(f"    Warning: Could not generate class {class_name}: {e}")
        return False


def generate_base_event_schema(ocsf: OcsfJsonSchemaEmbedded, output_dir: Path,
                               ocsf_version: str, all_objects: set) -> bool:
    """Generate the base OcsfEvent schema with common event fields."""
    try:
        # Get a sample class schema to extract base event fields
        sample_schema = ocsf.get_class_schema(class_name='detection_finding', profiles=[])

        result = {
            "$schema": "http://json-schema.org/draft-07/schema#",
            "$id": f"https://schema.ocsf.io/{ocsf_version}/base_event",
            "title": "OcsfEvent",
            "description": "Base OCSF Event - contains common attributes shared by all event classes",
            "type": "object",
            "javaType": f"{JAVA_PACKAGE}.OcsfEvent",
            "additionalProperties": True,
            "properties": {},
            "required": ["class_uid", "category_uid", "type_uid", "time", "severity_id"]
        }

        # Base event properties to include
        # Note: Timestamp fields (time, start_time, end_time, duration) use javaType Long
        # because epoch milliseconds exceed Integer.MAX_VALUE
        base_fields = {
            'activity_id': {'type': 'integer', 'description': 'The normalized identifier of the activity that triggered the event.'},
            'activity_name': {'type': 'string', 'description': 'The event activity name, as defined by the activity_id.'},
            'category_name': {'type': 'string', 'description': 'The event category name, as defined by category_uid value.'},
            'category_uid': {'type': 'integer', 'description': 'The category unique identifier of the event.'},
            'class_name': {'type': 'string', 'description': 'The event class name, as defined by class_uid value.'},
            'class_uid': {'type': 'integer', 'description': 'The unique identifier of a class.'},
            'count': {'type': 'integer', 'description': 'The number of times that events in the same logical group occurred during the event Start Time to End Time period.'},
            'duration': {'type': 'integer', 'existingJavaType': 'java.lang.Long', 'description': 'The event duration or aggregate time, in milliseconds.'},
            'end_time': {'type': 'integer', 'existingJavaType': 'java.lang.Long', 'description': 'The end time of a time period, or the time of the most recent event included in the aggregate event.'},
            'end_time_dt': {'type': 'string', 'format': 'date-time', 'description': 'The end time as a datetime string.'},
            'message': {'type': 'string', 'description': 'The description of the event, as defined by the event source.'},
            'raw_data': {'type': 'string', 'description': 'The event data as received from the event source.'},
            'severity': {'type': 'string', 'description': 'The event severity, normalized to the caption of the severity_id value.'},
            'severity_id': {'type': 'integer', 'description': 'The normalized identifier of the event severity.'},
            'start_time': {'type': 'integer', 'existingJavaType': 'java.lang.Long', 'description': 'The start time of a time period, or the time of the least recent event included in the aggregate event.'},
            'start_time_dt': {'type': 'string', 'format': 'date-time', 'description': 'The start time as a datetime string.'},
            'status': {'type': 'string', 'description': 'The event status, normalized to the caption of the status_id value.'},
            'status_code': {'type': 'string', 'description': 'The event status code, as reported by the event source.'},
            'status_detail': {'type': 'string', 'description': 'The status details contains additional information about the event outcome.'},
            'status_id': {'type': 'integer', 'description': 'The normalized identifier of the event status.'},
            'time': {'type': 'integer', 'existingJavaType': 'java.lang.Long', 'description': 'The normalized event occurrence time, in milliseconds since epoch.'},
            'time_dt': {'type': 'string', 'format': 'date-time', 'description': 'The time as a datetime string.'},
            'timezone_offset': {'type': 'integer', 'description': 'The number of minutes that the reported event time is ahead or behind UTC.'},
            'type_name': {'type': 'string', 'description': 'The event type name.'},
            'type_uid': {'type': 'integer', 'description': 'The event type ID.'},
            'version': {'type': 'string', 'description': 'The version of the OCSF schema.'},
        }

        result['properties'] = base_fields

        # Add metadata reference
        result['properties']['metadata'] = {"$ref": "Metadata.json"}

        # Add observables
        result['properties']['observables'] = {
            'type': 'array',
            'description': 'The observables associated with the event.',
            'items': {"$ref": "Observable.json"}
        }

        # Add unmapped
        result['properties']['unmapped'] = {
            'type': 'object',
            'description': 'The attributes that are not mapped to the event schema.',
            'additionalProperties': True
        }

        # Write to file
        output_file = output_dir / "OcsfEvent.json"
        with open(output_file, 'w') as f:
            json.dump(result, f, indent=2)

        return True

    except Exception as e:
        print(f"    Error generating OcsfEvent: {e}")
        return False


def main():
    parser = argparse.ArgumentParser(
        description='Generate OCSF JSON Schema files for Apache Camel (jsonschema2pojo compatible)'
    )
    parser.add_argument(
        '--version', '-v',
        default='1.7.0',
        help='OCSF schema version (default: 1.7.0)'
    )
    parser.add_argument(
        '--output', '-o',
        default='../resources/schema',
        help='Output directory for schema files (default: ../resources/schema)'
    )
    parser.add_argument(
        '--all-classes',
        action='store_true',
        help='Generate schemas for all available classes (not just the default list)'
    )
    parser.add_argument(
        '--all-objects',
        action='store_true',
        help='Generate schemas for all available objects (not just the default list)'
    )
    parser.add_argument(
        '--clean',
        action='store_true',
        help='Remove existing schema files before generating'
    )

    args = parser.parse_args()

    # Resolve output directory
    script_dir = Path(__file__).parent
    output_dir = (script_dir / args.output).resolve()

    print(f"OCSF JSON Schema Generator for Apache Camel")
    print(f"============================================")
    print(f"OCSF Version: {args.version}")
    print(f"Output Directory: {output_dir}")
    print(f"Java Package: {JAVA_PACKAGE}")
    print()

    # Clean output directory if requested
    if args.clean and output_dir.exists():
        print("Cleaning output directory...")
        for f in output_dir.glob("*.json"):
            f.unlink()

    # Create output directory
    output_dir.mkdir(parents=True, exist_ok=True)

    # Load OCSF schema
    print(f"Loading OCSF schema version {args.version}...")
    try:
        ocsf_schema = get_ocsf_schema(version=args.version)
        ocsf = OcsfJsonSchemaEmbedded(ocsf_schema)
    except Exception as e:
        print(f"Error loading OCSF schema: {e}")
        sys.exit(1)

    # Determine which classes and objects to generate
    if args.all_classes:
        # Filter out Windows-specific classes
        classes = [c for c in ocsf_schema.get('classes', {}).keys()
                   if not c.startswith('win/')]
    else:
        classes = CLASSES_TO_GENERATE

    if args.all_objects:
        # Filter out Windows-specific objects
        objects = [o for o in ocsf_schema.get('objects', {}).keys()
                   if not o.startswith('win/')]
    else:
        objects = OBJECTS_TO_GENERATE

    # Create set of all object names for reference resolution
    all_objects = set(objects)

    # Generate base event schema first
    print()
    print("Generating base event schema...")
    if generate_base_event_schema(ocsf, output_dir, args.version, all_objects):
        print("  Generated: OcsfEvent.json")

    # Generate object schemas
    print()
    print(f"Generating object schemas ({len(objects)} objects)...")
    object_count = 0
    for object_name in sorted(objects):
        if generate_object_schema(ocsf, object_name, output_dir, args.version, all_objects):
            print(f"  Generated: {to_pascal_case(object_name)}.json")
            object_count += 1

    # Generate class schemas
    print()
    print(f"Generating class schemas ({len(classes)} classes)...")
    class_count = 0
    for class_name in sorted(classes):
        if generate_class_schema(ocsf, class_name, output_dir, args.version, all_objects):
            print(f"  Generated: {to_pascal_case(class_name)}.json")
            class_count += 1

    print()
    print(f"Done!")
    print(f"  Generated {object_count} object schemas")
    print(f"  Generated {class_count} class schemas")
    print(f"  Total: {object_count + class_count + 1} files (including OcsfEvent.json)")
    print()
    print(f"Schemas written to: {output_dir}")
    print()
    print("Next steps:")
    print("  1. Review generated schemas for any missing references")
    print("  2. Run 'mvn compile' to generate Java classes")
    print("  3. Run 'mvn test' to verify the generated classes work correctly")


if __name__ == '__main__':
    main()
