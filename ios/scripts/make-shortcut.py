#!/usr/bin/env python3
"""Builds the "TextFox Send" shortcut: Get Due TextFox Macros → for each: message, recipients,
Send Message. Writes an unsigned file; make-shortcut.sh signs it with `shortcuts sign`."""
import plistlib, sys, uuid

BUNDLE, TEAM, APP = "app.kodekid.textfox", "F545QS7GV6", "TextFox"

def uid(): return str(uuid.uuid4()).upper()

def intent(name, uuid_, **params):
    p = {"AppIntentDescriptor": {"BundleIdentifier": BUNDLE, "Name": APP, "TeamIdentifier": TEAM,
                                 "AppIntentIdentifier": name, "ActionRequiresAppInstallation": True},
         "UUID": uuid_, **params}
    return {"WFWorkflowActionIdentifier": f"{BUNDLE}.{name}", "WFWorkflowActionParameters": p}

def output(uuid_, name): return {"Type": "ActionOutput", "OutputUUID": uuid_, "OutputName": name}
def attach(value): return {"Value": value, "WFSerializationType": "WFTextTokenAttachment"}
def text(value): return {"Value": {"string": "￼", "attachmentsByRange": {"{0, 1}": value}}, "WFSerializationType": "WFTextTokenString"}
repeat_item = {"Type": "Variable", "VariableName": "Repeat Item"}

due, msg, rcp, group = uid(), uid(), uid(), uid()
actions = [
    intent("GetDueMacrosIntent", due),
    {"WFWorkflowActionIdentifier": "is.workflow.actions.repeat.each",
     "WFWorkflowActionParameters": {"WFInput": attach(output(due, "Due Macros")), "GroupingIdentifier": group, "WFControlFlowMode": 0}},
    intent("GetMessageIntent", msg, macro=attach(repeat_item)),
    intent("GetRecipientsIntent", rcp, macro=attach(repeat_item)),
    {"WFWorkflowActionIdentifier": "is.workflow.actions.sendmessage",
     "WFWorkflowActionParameters": {"WFSendMessageContent": text(output(msg, "Message")),
                                    "WFSendMessageActionRecipients": attach(output(rcp, "Recipients")),
                                    "ShowWhenRun": False, "UUID": uid()}},
    {"WFWorkflowActionIdentifier": "is.workflow.actions.repeat.each",
     "WFWorkflowActionParameters": {"GroupingIdentifier": group, "WFControlFlowMode": 2, "UUID": uid()}},
]
shortcut = {
    "WFWorkflowActions": actions,
    "WFWorkflowClientVersion": "2607.0.2",
    "WFWorkflowMinimumClientVersion": 900,
    "WFWorkflowMinimumClientVersionString": "900",
    "WFWorkflowIcon": {"WFWorkflowIconStartColor": 4292093695, "WFWorkflowIconGlyphNumber": 61440},
    "WFWorkflowImportQuestions": [],
    "WFWorkflowInputContentItemClasses": [],
    "WFWorkflowOutputContentItemClasses": [],
    "WFWorkflowTypes": [],
    "WFQuickActionSurfaces": [],
    "WFWorkflowHasShortcutInputVariables": False,
    "WFWorkflowHasOutputFallback": False,
}
with open(sys.argv[1], "wb") as f: plistlib.dump(shortcut, f, fmt=plistlib.FMT_BINARY)
