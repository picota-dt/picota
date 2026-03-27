import {useEffect, useRef, useState} from "react";
import Editor from "@monaco-editor/react";
import {AlertTriangle, Save, Send} from "lucide-react";
import {DigitalTwin} from "../../context/AppContext";

// ─── Semver helpers ────────────────────────────────────────────────────────────

function bumpVersion(version: string, type: "patch" | "minor" | "major"): string {
    const parts = version.split(".").map(Number);
    const [major, minor, patch] = parts.length === 3 ? parts : [0, 1, 0];
    if (type === "patch") return `${major}.${minor}.${patch + 1}`;
    if (type === "minor") return `${major}.${minor + 1}.0`;
    return `${major + 1}.0.0`;
}

const LSP_MARKER_OWNER = "picota-model-lsp";
const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL ?? "/v1").replace(/\/+$/, "");
const TOKEN_STORAGE_KEY = "picota.auth.token";
const TARA_LANGUAGE_ID = "tara";
const TARA_THEME_ID = "picota-tara-dark";
const LSP_RECONNECT_BASE_DELAY_MS = 600;
const LSP_RECONNECT_MAX_DELAY_MS = 5000;
let taraLanguageConfigured = false;
let taraSemanticTokensProviderConfigured = false;
let taraSemanticTokensProviderDisposable: { dispose: () => void } | null = null;
let taraThemeConfigured = false;

const DEFAULT_SEMANTIC_TOKEN_TYPES = [
    "namespace",
    "type",
    "class",
    "enum",
    "interface",
    "struct",
    "typeParameter",
    "parameter",
    "variable",
    "property",
    "enumMember",
    "event",
    "function",
    "method",
    "macro",
    "keyword",
    "modifier",
    "comment",
    "string",
    "number",
    "regexp",
    "operator",
    "decorator",
];

const DEFAULT_SEMANTIC_TOKEN_MODIFIERS = [
    "declaration",
    "definition",
    "readonly",
    "static",
    "deprecated",
    "abstract",
    "async",
    "modification",
    "documentation",
    "defaultLibrary",
];

let taraSemanticLegend = {
    tokenTypes: DEFAULT_SEMANTIC_TOKEN_TYPES,
    tokenModifiers: DEFAULT_SEMANTIC_TOKEN_MODIFIERS,
};

const semanticTokensListeners = new Set<() => void>();
const lspClientsByUri = new Map<string, MonacoLspClient>();
let taraLspCapabilities: any = {};

let taraCompletionProviderDisposable: { dispose: () => void } | null = null;
let taraRenameProviderDisposable: { dispose: () => void } | null = null;
let taraFoldingProviderDisposable: { dispose: () => void } | null = null;
let taraSignatureHelpProviderDisposable: { dispose: () => void } | null = null;

function onSemanticTokensDidChange(listener: () => void) {
    semanticTokensListeners.add(listener);
    return {
        dispose: () => semanticTokensListeners.delete(listener),
    };
}

function refreshSemanticTokens() {
    for (const listener of semanticTokensListeners) listener();
}

function ensureTaraSemanticTokensProvider(monaco: any, legend: { tokenTypes: string[]; tokenModifiers: string[] }) {
    taraSemanticLegend = legend;
    if (taraSemanticTokensProviderDisposable) {
        taraSemanticTokensProviderDisposable.dispose();
        taraSemanticTokensProviderDisposable = null;
    }
    taraSemanticTokensProviderDisposable = monaco.languages.registerDocumentSemanticTokensProvider(TARA_LANGUAGE_ID, {
        getLegend: () => taraSemanticLegend,
        onDidChange: onSemanticTokensDidChange,
        provideDocumentSemanticTokens: async (model: any) => {
            const uri = model?.uri?.toString?.() ?? "";
            const client = lspClientsByUri.get(uri);
            if (!client) return {data: new Uint32Array(0)};
            return client.provideSemanticTokens();
        },
        releaseDocumentSemanticTokens: () => {
        },
    });
    taraSemanticTokensProviderConfigured = true;
    refreshSemanticTokens();
}

function getClientForModel(model: any): MonacoLspClient | null {
    const uri = model?.uri?.toString?.() ?? "";
    return lspClientsByUri.get(uri) ?? null;
}

function toLspPosition(position: any) {
    return {
        line: Math.max(0, (position?.lineNumber ?? 1) - 1),
        character: Math.max(0, (position?.column ?? 1) - 1),
    };
}

function toMonacoRange(range: any) {
    if (!range?.start || !range?.end) return null;
    return {
        startLineNumber: (range.start.line ?? 0) + 1,
        startColumn: (range.start.character ?? 0) + 1,
        endLineNumber: (range.end.line ?? 0) + 1,
        endColumn: (range.end.character ?? 0) + 1,
    };
}

function toMonacoMarkdown(value: any) {
    if (typeof value === "string") return value;
    if (value && typeof value === "object") {
        if (typeof value.value === "string") return {value: value.value};
        return {value: JSON.stringify(value)};
    }
    return undefined;
}

function toLspCompletionTriggerKind(monacoTriggerKind: number | undefined) {
    switch (monacoTriggerKind) {
        case 1:
            return 2; // TriggerCharacter
        case 2:
            return 3; // TriggerForIncompleteCompletions
        default:
            return 1; // Invoked
    }
}

function toLspSignatureTriggerKind(monacoTriggerKind: number | undefined) {
    switch (monacoTriggerKind) {
        case 2:
            return 2; // TriggerCharacter
        case 3:
            return 3; // ContentChange
        default:
            return 1; // Invoked
    }
}

function toMonacoCompletionKind(monaco: any, lspKind: number | undefined) {
    switch (lspKind) {
        case 1:
            return monaco.languages.CompletionItemKind.Text;
        case 2:
            return monaco.languages.CompletionItemKind.Method;
        case 3:
            return monaco.languages.CompletionItemKind.Function;
        case 4:
            return monaco.languages.CompletionItemKind.Constructor;
        case 5:
            return monaco.languages.CompletionItemKind.Field;
        case 6:
            return monaco.languages.CompletionItemKind.Variable;
        case 7:
            return monaco.languages.CompletionItemKind.Class;
        case 8:
            return monaco.languages.CompletionItemKind.Interface;
        case 9:
            return monaco.languages.CompletionItemKind.Module;
        case 10:
            return monaco.languages.CompletionItemKind.Property;
        case 11:
            return monaco.languages.CompletionItemKind.Unit;
        case 12:
            return monaco.languages.CompletionItemKind.Value;
        case 13:
            return monaco.languages.CompletionItemKind.Enum;
        case 14:
            return monaco.languages.CompletionItemKind.Keyword;
        case 15:
            return monaco.languages.CompletionItemKind.Snippet;
        case 16:
            return monaco.languages.CompletionItemKind.Color;
        case 17:
            return monaco.languages.CompletionItemKind.File;
        case 18:
            return monaco.languages.CompletionItemKind.Reference;
        case 19:
            return monaco.languages.CompletionItemKind.Folder;
        case 20:
            return monaco.languages.CompletionItemKind.EnumMember;
        case 21:
            return monaco.languages.CompletionItemKind.Constant;
        case 22:
            return monaco.languages.CompletionItemKind.Struct;
        case 23:
            return monaco.languages.CompletionItemKind.Event;
        case 24:
            return monaco.languages.CompletionItemKind.Operator;
        case 25:
            return monaco.languages.CompletionItemKind.TypeParameter;
        default:
            return monaco.languages.CompletionItemKind.Text;
    }
}

function toMonacoCompletionItem(monaco: any, model: any, position: any, item: any) {
    const label = typeof item?.label === "string"
        ? item.label
        : typeof item?.label?.label === "string"
            ? item.label.label
            : "";
    if (!label) return null;

    const word = model.getWordUntilPosition(position);
    const defaultRange = {
        startLineNumber: position.lineNumber,
        startColumn: word?.startColumn ?? position.column,
        endLineNumber: position.lineNumber,
        endColumn: word?.endColumn ?? position.column,
    };

    let range: any = defaultRange;
    let insertText = typeof item?.insertText === "string" ? item.insertText : label;

    const textEdit = item?.textEdit;
    if (textEdit?.range && typeof textEdit?.newText === "string") {
        const monacoRange = toMonacoRange(textEdit.range);
        if (monacoRange) range = monacoRange;
        insertText = textEdit.newText;
    } else if (textEdit?.insert && textEdit?.replace && typeof textEdit?.newText === "string") {
        const insertRange = toMonacoRange(textEdit.insert);
        const replaceRange = toMonacoRange(textEdit.replace);
        if (insertRange && replaceRange) {
            range = {insert: insertRange, replace: replaceRange};
        }
        insertText = textEdit.newText;
    }

    const additionalTextEdits = Array.isArray(item?.additionalTextEdits)
        ? item.additionalTextEdits
            .map((edit: any) => {
                const editRange = toMonacoRange(edit?.range);
                if (!editRange) return null;
                return {range: editRange, text: String(edit?.newText ?? "")};
            })
            .filter(Boolean)
        : undefined;

    return {
        label,
        kind: toMonacoCompletionKind(monaco, item?.kind),
        detail: typeof item?.detail === "string" ? item.detail : undefined,
        documentation: toMonacoMarkdown(item?.documentation),
        sortText: typeof item?.sortText === "string" ? item.sortText : undefined,
        filterText: typeof item?.filterText === "string" ? item.filterText : undefined,
        preselect: Boolean(item?.preselect),
        insertText,
        insertTextRules: item?.insertTextFormat === 2 ? monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet : undefined,
        range,
        commitCharacters: Array.isArray(item?.commitCharacters) ? item.commitCharacters : undefined,
        additionalTextEdits,
        command: item?.command && typeof item.command.command === "string"
            ? {
                id: item.command.command,
                title: item.command.title ?? item.command.command,
                arguments: item.command.arguments
            }
            : undefined,
    };
}

function toMonacoWorkspaceEdit(monaco: any, workspaceEdit: any) {
    const edits: any[] = [];

    if (workspaceEdit?.changes && typeof workspaceEdit.changes === "object") {
        for (const [uri, textEdits] of Object.entries(workspaceEdit.changes)) {
            if (!Array.isArray(textEdits)) continue;
            for (const edit of textEdits) {
                const range = toMonacoRange((edit as any)?.range);
                if (!range) continue;
                edits.push({
                    resource: monaco.Uri.parse(uri),
                    textEdit: {range, text: String((edit as any)?.newText ?? "")},
                    versionId: undefined,
                });
            }
        }
    }

    if (Array.isArray(workspaceEdit?.documentChanges)) {
        for (const change of workspaceEdit.documentChanges) {
            if (!change || typeof change !== "object") continue;
            if ("kind" in change) continue;
            const uri = change?.textDocument?.uri;
            if (typeof uri !== "string") continue;
            const textEdits = Array.isArray(change?.edits) ? change.edits : [];
            for (const edit of textEdits) {
                const range = toMonacoRange(edit?.range);
                if (!range) continue;
                edits.push({
                    resource: monaco.Uri.parse(uri),
                    textEdit: {range, text: String(edit?.newText ?? "")},
                    versionId: undefined,
                });
            }
        }
    }

    return {edits};
}

function toMonacoSignatureHelp(signatureHelp: any) {
    if (!signatureHelp || !Array.isArray(signatureHelp.signatures)) return null;
    const signatures = signatureHelp.signatures.map((signature: any) => ({
        label: String(signature?.label ?? ""),
        documentation: toMonacoMarkdown(signature?.documentation),
        parameters: Array.isArray(signature?.parameters)
            ? signature.parameters.map((parameter: any) => ({
                label: Array.isArray(parameter?.label) ? [parameter.label[0] ?? 0, parameter.label[1] ?? 0] : String(parameter?.label ?? ""),
                documentation: toMonacoMarkdown(parameter?.documentation),
            }))
            : [],
        activeParameter: typeof signature?.activeParameter === "number" ? signature.activeParameter : undefined,
    }));
    return {
        signatures,
        activeSignature: typeof signatureHelp?.activeSignature === "number" ? signatureHelp.activeSignature : 0,
        activeParameter: typeof signatureHelp?.activeParameter === "number" ? signatureHelp.activeParameter : 0,
    };
}

function toMonacoFoldingKind(monaco: any, kind: any) {
    if (typeof kind !== "string" || kind.length === 0) return undefined;
    return monaco.languages.FoldingRangeKind.fromValue(kind);
}

function configureTaraLspFeatureProviders(monaco: any, capabilities: any) {
    taraLspCapabilities = capabilities ?? {};

    if (taraCompletionProviderDisposable) {
        taraCompletionProviderDisposable.dispose();
        taraCompletionProviderDisposable = null;
    }
    if (taraRenameProviderDisposable) {
        taraRenameProviderDisposable.dispose();
        taraRenameProviderDisposable = null;
    }
    if (taraFoldingProviderDisposable) {
        taraFoldingProviderDisposable.dispose();
        taraFoldingProviderDisposable = null;
    }
    if (taraSignatureHelpProviderDisposable) {
        taraSignatureHelpProviderDisposable.dispose();
        taraSignatureHelpProviderDisposable = null;
    }

    const completionProvider = taraLspCapabilities?.completionProvider;
    taraCompletionProviderDisposable = monaco.languages.registerCompletionItemProvider(TARA_LANGUAGE_ID, {
        triggerCharacters: Array.isArray(completionProvider?.triggerCharacters) ? completionProvider.triggerCharacters : [],
        provideCompletionItems: async (model: any, position: any, context: any) => {
            if (!taraLspCapabilities?.completionProvider) return {suggestions: []};
            const client = getClientForModel(model);
            if (!client) return {suggestions: []};
            try {
                const completion = await client.requestCompletion(
                    toLspPosition(position),
                    {
                        triggerKind: toLspCompletionTriggerKind(context?.triggerKind),
                        triggerCharacter: context?.triggerCharacter,
                    },
                );
                const items = Array.isArray(completion)
                    ? completion
                    : Array.isArray(completion?.items)
                        ? completion.items
                        : [];
                return {
                    suggestions: items
                        .map((item: any) => toMonacoCompletionItem(monaco, model, position, item))
                        .filter(Boolean),
                    incomplete: Boolean(completion?.isIncomplete),
                };
            } catch {
                return {suggestions: []};
            }
        },
    });

    taraRenameProviderDisposable = monaco.languages.registerRenameProvider(TARA_LANGUAGE_ID, {
        provideRenameEdits: async (model: any, position: any, newName: string) => {
            if (!taraLspCapabilities?.renameProvider) {
                return {edits: [], rejectReason: "Rename is not supported by the language server"};
            }
            const client = getClientForModel(model);
            if (!client) return {edits: [], rejectReason: "Language server is not connected"};
            try {
                const workspaceEdit = await client.requestRename(toLspPosition(position), newName);
                if (!workspaceEdit) return {edits: [], rejectReason: "Rename returned no changes"};
                return toMonacoWorkspaceEdit(monaco, workspaceEdit);
            } catch (error: any) {
                return {edits: [], rejectReason: String(error?.message ?? "Rename request failed")};
            }
        },
        resolveRenameLocation: async (model: any, position: any) => {
            if (!taraLspCapabilities?.renameProvider) {
                return {rejectReason: "Rename is not supported by the language server"};
            }
            const client = getClientForModel(model);
            if (!client) return {rejectReason: "Language server is not connected"};
            try {
                const prepared = await client.prepareRename(toLspPosition(position));
                if (prepared?.defaultBehavior) {
                    const word = model.getWordAtPosition(position);
                    if (word) {
                        return {
                            range: {
                                startLineNumber: position.lineNumber,
                                startColumn: word.startColumn,
                                endLineNumber: position.lineNumber,
                                endColumn: word.endColumn,
                            },
                            text: word.word,
                        };
                    }
                }
                const preparedRange = toMonacoRange(prepared?.range ?? prepared);
                if (preparedRange) {
                    const text = typeof prepared?.placeholder === "string"
                        ? prepared.placeholder
                        : model.getValueInRange(preparedRange);
                    return {range: preparedRange, text: String(text ?? "")};
                }
            } catch (error: any) {
                return {rejectReason: String(error?.message ?? "Rename not available at this position")};
            }
            return {rejectReason: "Rename not available at this position"};
        },
    });

    taraFoldingProviderDisposable = monaco.languages.registerFoldingRangeProvider(TARA_LANGUAGE_ID, {
        provideFoldingRanges: async (model: any) => {
            if (!taraLspCapabilities?.foldingRangeProvider) return [];
            const client = getClientForModel(model);
            if (!client) return [];
            try {
                const ranges = await client.requestFoldingRanges();
                if (!Array.isArray(ranges)) return [];
                return ranges
                    .map((range: any) => {
                        const start = (range?.startLine ?? 0) + 1;
                        const end = (range?.endLine ?? range?.startLine ?? 0) + 1;
                        if (end < start) return null;
                        return {
                            start,
                            end,
                            kind: toMonacoFoldingKind(monaco, range?.kind),
                        };
                    })
                    .filter(Boolean);
            } catch {
                return [];
            }
        },
    });

    const signatureHelpProvider = taraLspCapabilities?.signatureHelpProvider;
    taraSignatureHelpProviderDisposable = monaco.languages.registerSignatureHelpProvider(TARA_LANGUAGE_ID, {
        signatureHelpTriggerCharacters: Array.isArray(signatureHelpProvider?.triggerCharacters) ? signatureHelpProvider.triggerCharacters : [],
        signatureHelpRetriggerCharacters: Array.isArray(signatureHelpProvider?.retriggerCharacters) ? signatureHelpProvider.retriggerCharacters : [],
        provideSignatureHelp: async (model: any, position: any, _token: any, context: any) => {
            if (!taraLspCapabilities?.signatureHelpProvider) return null;
            const client = getClientForModel(model);
            if (!client) return null;
            try {
                const signatureHelp = await client.requestSignatureHelp(
                    toLspPosition(position),
                    {
                        triggerKind: toLspSignatureTriggerKind(context?.triggerKind),
                        triggerCharacter: context?.triggerCharacter,
                        isRetrigger: Boolean(context?.isRetrigger),
                    },
                );
                const value = toMonacoSignatureHelp(signatureHelp);
                if (!value) return null;
                return {
                    value,
                    dispose: () => {
                    },
                };
            } catch {
                return null;
            }
        },
    });
}

function ensureTaraTheme(monaco: any) {
    if (taraThemeConfigured) return;
    monaco.editor.defineTheme(TARA_THEME_ID, {
        base: "vs-dark",
        inherit: true,
        rules: [
            {token: "class", foreground: "4EC9B0"},
            {token: "type", foreground: "4EC9B0"},
            {token: "interface", foreground: "B8D7A3"},
            {token: "enum", foreground: "B5CEA8"},
            {token: "string", foreground: "CE9178"},
            {token: "keyword", foreground: "569CD6"},
        ],
        colors: {},
    });
    taraThemeConfigured = true;
}

function ensureTaraLanguage(monaco: any) {
    ensureTaraTheme(monaco);
    if (!taraLanguageConfigured) {
        monaco.languages.register({id: TARA_LANGUAGE_ID});
        monaco.languages.setLanguageConfiguration(TARA_LANGUAGE_ID, {
            autoClosingPairs: [
                {open: "{", close: "}"},
                {open: "[", close: "]"},
                {open: "(", close: ")"},
                {open: "\"", close: "\"", notIn: ["string", "comment"]},
            ],
            surroundingPairs: [
                {open: "{", close: "}"},
                {open: "[", close: "]"},
                {open: "(", close: ")"},
                {open: "\"", close: "\""},
            ],
        });
        taraLanguageConfigured = true;
    }

    if (!taraSemanticTokensProviderConfigured) {
        ensureTaraSemanticTokensProvider(monaco, taraSemanticLegend);
    }

    if (!taraCompletionProviderDisposable || !taraRenameProviderDisposable || !taraFoldingProviderDisposable || !taraSignatureHelpProviderDisposable) {
        configureTaraLspFeatureProviders(monaco, taraLspCapabilities);
    }
}

function resolveModelLspWebSocketUrl(): string {
    const baseUrl = new URL(API_BASE_URL, window.location.origin);
    const wsProtocol = baseUrl.protocol === "https:" ? "wss:" : "ws:";
    const wsUrl = new URL(`${wsProtocol}//${baseUrl.host}/lsp/model`);
    const token = localStorage.getItem(TOKEN_STORAGE_KEY);
    if (token && token.trim().length > 0) {
        wsUrl.searchParams.set("token", token.trim());
    }
    return wsUrl.toString();
}

function modelDocumentUri(twinId: string): string {
    return `inmemory://picota/twins/${twinId}.tara`;
}

class MonacoLspClient {
    private readonly pending = new Map<number, {
        resolve: (value: unknown) => void;
        reject: (reason?: unknown) => void
    }>();
    private ws: WebSocket | null = null;
    private nextRequestId = 1;
    private initialized = false;
    private opened = false;
    private version = 1;
    private lastText = "";
    private serverCapabilities: any = {};
    private reconnectTimer: number | null = null;
    private reconnectAttempts = 0;
    private disposed = false;
    private lastSemanticTokens: { data: Uint32Array; resultId?: string } = {data: new Uint32Array(0)};
    private readonly supportedSemanticTokenTypes: string[];
    private readonly supportedSemanticTokenModifiers: string[];

    constructor(
        private readonly monaco: any,
        private readonly model: any,
        private readonly uri: string,
        private readonly onErrorCountChange?: (count: number) => void,
    ) {
        const monacoTypes = Object.values(this.monaco?.languages?.SemanticTokenTypes ?? {}).filter((value): value is string => typeof value === "string");
        const monacoModifiers = Object.values(this.monaco?.languages?.SemanticTokenModifiers ?? {}).filter((value): value is string => typeof value === "string");
        this.supportedSemanticTokenTypes = monacoTypes.length > 0 ? monacoTypes : DEFAULT_SEMANTIC_TOKEN_TYPES;
        this.supportedSemanticTokenModifiers = monacoModifiers.length > 0 ? monacoModifiers : DEFAULT_SEMANTIC_TOKEN_MODIFIERS;
    }

    start(initialText: string) {
        this.disposed = false;
        this.lastText = initialText ?? "";
        lspClientsByUri.set(this.uri, this);
        this.connect();
    }

    sync(nextText: string) {
        const safeText = nextText ?? "";
        if (safeText === this.lastText) return;
        this.lastText = safeText;
        this.version += 1;
        if (!this.initialized || !this.opened || !this.ws || this.ws.readyState !== WebSocket.OPEN) {
            return;
        }
        this.sendNotification("textDocument/didChange", {
            textDocument: {uri: this.uri, version: this.version},
            contentChanges: [{text: safeText}],
        });
    }

    dispose() {
        this.disposed = true;
        this.clearReconnectTimer();
        lspClientsByUri.delete(this.uri);
        this.onErrorCountChange?.(0);
        if (this.initialized && this.opened && this.ws?.readyState === WebSocket.OPEN) {
            this.sendNotification("textDocument/didClose", {textDocument: {uri: this.uri}});
        }
        this.opened = false;
        this.initialized = false;
        this.pending.clear();
        this.lastSemanticTokens = {data: new Uint32Array(0)};
        this.monaco.editor.setModelMarkers(this.model, LSP_MARKER_OWNER, []);
        if (this.ws) {
            this.ws.onopen = null;
            this.ws.onmessage = null;
            this.ws.onerror = null;
            this.ws.onclose = null;
            this.ws.close();
            this.ws = null;
        }
    }

    async provideSemanticTokens() {
        if (!this.initialized || !this.opened || !this.ws || this.ws.readyState !== WebSocket.OPEN) {
            return this.lastSemanticTokens;
        }
        try {
            const tokens: any = await this.sendRequest("textDocument/semanticTokens/full", {
                textDocument: {uri: this.uri},
            });
            const data = Array.isArray(tokens?.data) ? new Uint32Array(tokens.data) : new Uint32Array(0);
            if (typeof tokens?.resultId === "string") {
                this.lastSemanticTokens = {data, resultId: tokens.resultId};
                return this.lastSemanticTokens;
            }
            this.lastSemanticTokens = {data};
            return this.lastSemanticTokens;
        } catch {
            return this.lastSemanticTokens;
        }
    }

    async requestCompletion(position: any, context: any) {
        if (!this.initialized || !this.opened || !this.ws || this.ws.readyState !== WebSocket.OPEN) {
            return null;
        }
        return this.sendRequest("textDocument/completion", {
            textDocument: {uri: this.uri},
            position,
            context,
        });
    }

    async requestRename(position: any, newName: string) {
        if (!this.initialized || !this.opened || !this.ws || this.ws.readyState !== WebSocket.OPEN) {
            return null;
        }
        return this.sendRequest("textDocument/rename", {
            textDocument: {uri: this.uri},
            position,
            newName,
        });
    }

    async prepareRename(position: any) {
        if (!this.initialized || !this.opened || !this.ws || this.ws.readyState !== WebSocket.OPEN) {
            return null;
        }
        return this.sendRequest("textDocument/prepareRename", {
            textDocument: {uri: this.uri},
            position,
        });
    }

    async requestFoldingRanges() {
        if (!this.initialized || !this.opened || !this.ws || this.ws.readyState !== WebSocket.OPEN) {
            return [];
        }
        return this.sendRequest("textDocument/foldingRange", {
            textDocument: {uri: this.uri},
        });
    }

    async requestSignatureHelp(position: any, context: any) {
        if (!this.initialized || !this.opened || !this.ws || this.ws.readyState !== WebSocket.OPEN) {
            return null;
        }
        return this.sendRequest("textDocument/signatureHelp", {
            textDocument: {uri: this.uri},
            position,
            context,
        });
    }

    private initialize() {
        this.sendRequest("initialize", {
            processId: null,
            rootUri: null,
            capabilities: {
                textDocument: {
                    synchronization: {didSave: true, willSave: false, willSaveWaitUntil: false},
                    publishDiagnostics: {},
                    semanticTokens: {
                        dynamicRegistration: false,
                        requests: {range: true, full: true},
                        tokenTypes: this.supportedSemanticTokenTypes,
                        tokenModifiers: this.supportedSemanticTokenModifiers,
                        formats: ["relative"],
                        overlappingTokenSupport: true,
                        multilineTokenSupport: true,
                    },
                    completion: {
                        dynamicRegistration: false,
                        completionItem: {
                            snippetSupport: true,
                            insertReplaceSupport: true,
                            documentationFormat: ["markdown", "plaintext"],
                            deprecatedSupport: true,
                            preselectSupport: true,
                            commitCharactersSupport: true,
                            labelDetailsSupport: true,
                        },
                        contextSupport: true,
                    },
                    rename: {
                        dynamicRegistration: false,
                        prepareSupport: true,
                    },
                    foldingRange: {
                        dynamicRegistration: false,
                        lineFoldingOnly: true,
                    },
                    signatureHelp: {
                        dynamicRegistration: false,
                        signatureInformation: {
                            documentationFormat: ["markdown", "plaintext"],
                            parameterInformation: {labelOffsetSupport: true},
                        },
                        contextSupport: true,
                    },
                },
            },
            clientInfo: {name: "picota-frontend", version: "1.0.0"},
            workspaceFolders: null,
        }).then((result: any) => {
            this.serverCapabilities = result?.capabilities ?? {};
            configureTaraLspFeatureProviders(this.monaco, this.serverCapabilities);
            const legend = this.serverCapabilities?.semanticTokensProvider?.legend;
            if (Array.isArray(legend?.tokenTypes) && Array.isArray(legend?.tokenModifiers)) {
                taraSemanticLegend = {
                    tokenTypes: legend.tokenTypes,
                    tokenModifiers: legend.tokenModifiers,
                };
                ensureTaraSemanticTokensProvider(this.monaco, taraSemanticLegend);
            }
            this.initialized = true;
            this.sendNotification("initialized", {});
            this.sendNotification("textDocument/didOpen", {
                textDocument: {
                    uri: this.uri,
                    languageId: TARA_LANGUAGE_ID,
                    version: this.version,
                    text: this.lastText,
                },
            });
            this.opened = true;
            refreshSemanticTokens();
        }).catch(() => {
            this.handleSocketClosed("LSP initialization failed");
        });
    }

    private connect() {
        if (this.disposed) return;
        const ws = new WebSocket(resolveModelLspWebSocketUrl());
        this.ws = ws;
        ws.onopen = () => {
            if (this.disposed) {
                ws.close();
                return;
            }
            this.reconnectAttempts = 0;
            this.clearReconnectTimer();
            this.initialize();
        };
        ws.onmessage = (event) => this.onMessage(event.data);
        ws.onclose = () => this.handleSocketClosed("LSP connection closed");
        ws.onerror = () => {
            try {
                ws.close();
            } catch {
            }
        };
    }

    private handleSocketClosed(reason: string) {
        if (this.initialized || this.opened) {
            this.initialized = false;
            this.opened = false;
        }
        this.teardownPending(reason);
        if (this.ws) {
            const socket = this.ws;
            this.ws.onopen = null;
            this.ws.onmessage = null;
            this.ws.onerror = null;
            this.ws.onclose = null;
            try {
                if (socket.readyState === WebSocket.OPEN || socket.readyState === WebSocket.CONNECTING) {
                    socket.close();
                }
            } catch {
            }
            this.ws = null;
        }
        if (!this.disposed) this.scheduleReconnect();
    }

    private scheduleReconnect() {
        if (this.reconnectTimer !== null) return;
        const delay = Math.min(
            LSP_RECONNECT_MAX_DELAY_MS,
            LSP_RECONNECT_BASE_DELAY_MS * (2 ** this.reconnectAttempts),
        );
        this.reconnectAttempts += 1;
        this.reconnectTimer = window.setTimeout(() => {
            this.reconnectTimer = null;
            this.connect();
        }, delay);
    }

    private clearReconnectTimer() {
        if (this.reconnectTimer === null) return;
        window.clearTimeout(this.reconnectTimer);
        this.reconnectTimer = null;
    }

    private sendRequest(method: string, params: unknown): Promise<unknown> {
        if (!this.ws || this.ws.readyState !== WebSocket.OPEN) {
            return Promise.reject(new Error("LSP socket is not open"));
        }
        const id = this.nextRequestId++;
        const payload = {jsonrpc: "2.0", id, method, params};
        this.ws.send(JSON.stringify(payload));
        return new Promise((resolve, reject) => this.pending.set(id, {resolve, reject}));
    }

    private sendNotification(method: string, params: unknown) {
        if (!this.ws || this.ws.readyState !== WebSocket.OPEN) return;
        this.ws.send(JSON.stringify({jsonrpc: "2.0", method, params}));
    }

    private onMessage(rawData: unknown) {
        if (typeof rawData !== "string") return;
        let message: any;
        try {
            message = JSON.parse(rawData);
        } catch {
            return;
        }

        if (typeof message.id === "number" && (Object.prototype.hasOwnProperty.call(message, "result") || Object.prototype.hasOwnProperty.call(message, "error"))) {
            const pending = this.pending.get(message.id);
            if (!pending) return;
            this.pending.delete(message.id);
            if (message.error) pending.reject(message.error);
            else pending.resolve(message.result);
            return;
        }

        if (message.method === "textDocument/publishDiagnostics") {
            this.applyDiagnostics(message.params);
            return;
        }

        if (message.method === "workspace/semanticTokens/refresh") {
            refreshSemanticTokens();
            return;
        }

        if (typeof message.id !== "undefined") {
            this.sendResponseError(message.id, -32601, "Method not implemented");
        }
    }

    private sendResponseError(id: unknown, code: number, message: string) {
        if (!this.ws || this.ws.readyState !== WebSocket.OPEN) return;
        this.ws.send(JSON.stringify({
            jsonrpc: "2.0",
            id,
            error: {code, message},
        }));
    }

    private applyDiagnostics(params: any) {
        if (!params) return;
        const expectedPath = new URL(this.uri).pathname;
        if (params.uri !== this.uri && params.uri !== expectedPath) return;
        const diagnostics = Array.isArray(params.diagnostics) ? params.diagnostics : [];
        const errorCount = diagnostics.reduce(
            (count: number, diag: any) => count + (diag?.severity === 1 ? 1 : 0),
            0,
        );
        const markers = diagnostics.map((diag: any) => {
            const startLineNumber = (diag?.range?.start?.line ?? 0) + 1;
            const startColumn = (diag?.range?.start?.character ?? 0) + 1;
            const endLineNumber = Math.max(startLineNumber, (diag?.range?.end?.line ?? 0) + 1);
            const rawEndColumn = (diag?.range?.end?.character ?? startColumn) + 1;
            const endColumn = Math.max(startColumn + 1, rawEndColumn);
            return {
                startLineNumber,
                startColumn,
                endLineNumber,
                endColumn,
                message: String(diag?.message ?? "Unknown language server issue"),
                severity: this.toMonacoSeverity(diag?.severity),
                source: diag?.source ?? "lsp",
            };
        });
        this.monaco.editor.setModelMarkers(this.model, LSP_MARKER_OWNER, markers);
        this.onErrorCountChange?.(errorCount);
    }

    private toMonacoSeverity(severity: number | undefined): number {
        switch (severity) {
            case 1:
                return this.monaco.MarkerSeverity.Error;
            case 2:
                return this.monaco.MarkerSeverity.Warning;
            case 3:
                return this.monaco.MarkerSeverity.Info;
            case 4:
                return this.monaco.MarkerSeverity.Hint;
            default:
                return this.monaco.MarkerSeverity.Info;
        }
    }

    private teardownPending(reason: string) {
        for (const [, pending] of this.pending) {
            pending.reject(new Error(reason));
        }
        this.pending.clear();
    }
}

// ─── Save dialog ──────────────────────────────────────────────────────────────

interface SaveDialogProps {
    currentVersion: string;
    canSave: boolean;
    saveDisabledReason?: string;
    onSave: (newVersion: string) => void;
    onDiscard: () => void;
    onCancel?: () => void;
}

function SaveDialog({currentVersion, canSave, saveDisabledReason, onSave, onDiscard, onCancel}: SaveDialogProps) {
    const [selected, setSelected] = useState<"patch" | "minor" | "major">("patch");
    useEffect(() => {
        if (!onCancel) return;
        const onKeyDown = (event: KeyboardEvent) => {
            if (event.key !== "Escape") return;
            event.preventDefault();
            onCancel();
        };
        window.addEventListener("keydown", onKeyDown);
        return () => window.removeEventListener("keydown", onKeyDown);
    }, [onCancel]);
    const BUMPS: { type: "patch" | "minor" | "major"; label: string; desc: string }[] = [
        {type: "patch", label: "Patch", desc: "Bug fixes / minor tweaks"},
        {type: "minor", label: "Minor", desc: "New features, backward compatible"},
        {type: "major", label: "Major", desc: "Breaking changes"},
    ];

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
            <div className="absolute inset-0 bg-black/60 backdrop-blur-sm" onClick={onCancel}/>
            <div className="relative bg-[#1a1d27] border border-white/12 rounded-2xl w-full max-w-md shadow-2xl">
                <div className="p-5 border-b border-white/8 flex items-center gap-3">
                    <div
                        className="w-8 h-8 rounded-lg bg-amber-500/15 border border-amber-500/25 flex items-center justify-center">
                        <Save className="w-4 h-4 text-amber-400"/>
                    </div>
                    <div>
                        <h2 className="text-white" style={{fontWeight: 600}}>Save model changes</h2>
                        <p className="text-white/40 text-xs mt-0.5">Choose the version type for this release</p>
                    </div>
                </div>

                <div className="p-5 flex flex-col gap-3">
                    <div className="flex items-center gap-3 p-3 bg-white/4 rounded-xl border border-white/8 mb-1">
                        <span className="text-white/40 text-xs">Current version</span>
                        <span className="text-white text-sm font-mono"
                              style={{fontWeight: 600}}>v{currentVersion}</span>
                        <span className="text-white/20 mx-1">→</span>
                        <span className="text-cyan-400 text-sm font-mono" style={{fontWeight: 600}}>
              v{bumpVersion(currentVersion, selected)}
            </span>
                    </div>

                    {BUMPS.map(({type, label, desc}) => (
                        <button
                            key={type}
                            onClick={() => setSelected(type)}
                            className={`flex items-center justify-between px-4 py-3 rounded-xl border transition-all text-left ${
                                selected === type
                                    ? "bg-cyan-500/15 border-cyan-500/50"
                                    : "bg-white/4 border-white/10 hover:border-white/20"
                            }`}
                        >
                            <div>
                                <p className={`text-sm ${selected === type ? "text-cyan-300" : "text-white/70"}`}
                                   style={{fontWeight: 500}}>
                                    {label} <span
                                    className="font-mono text-xs opacity-70">v{bumpVersion(currentVersion, type)}</span>
                                </p>
                                <p className="text-white/30 text-xs mt-0.5">{desc}</p>
                            </div>
                            <div
                                className={`w-4 h-4 rounded-full border-2 flex-shrink-0 ${selected === type ? "border-cyan-400 bg-cyan-400" : "border-white/20"}`}/>
                        </button>
                    ))}

                    {!canSave && (
                        <div
                            className="px-3 py-2 rounded-lg border border-red-500/25 bg-red-500/10 text-red-300 text-xs">
                            {saveDisabledReason ?? "Fix model errors before saving a new version."}
                        </div>
                    )}

                    <div className="flex gap-3 pt-2">
                        <button
                            onClick={onCancel}
                            className="flex-1 py-2.5 rounded-xl border border-white/10 text-white/60 hover:text-white/80 text-sm transition-colors"
                        >
                            Continue editing
                        </button>
                        <button
                            onClick={onDiscard}
                            className="flex-1 py-2.5 rounded-xl border border-white/10 text-white/40 hover:text-white/60 text-sm transition-colors"
                        >
                            Discard changes
                        </button>
                        <button
                            onClick={() => canSave && onSave(bumpVersion(currentVersion, selected))}
                            disabled={!canSave}
                            className="flex-1 py-2.5 rounded-xl bg-cyan-500 hover:bg-cyan-400 disabled:bg-cyan-500/30 disabled:text-white/40 disabled:cursor-not-allowed text-white text-sm transition-colors shadow-lg shadow-cyan-500/20"
                            style={{fontWeight: 500}}
                        >
                            Save as v{bumpVersion(currentVersion, selected)}
                        </button>
                    </div>
                </div>
            </div>
        </div>
    );
}

// ─── Main ─────────────────────────────────────────────────────────────────────

interface Props {
    twin: DigitalTwin;
    onUpdateModel: (model: string, newVersion: string) => void;
    // Expose dirty state so parent can intercept tab changes
    onDirtyChange: (dirty: boolean) => void;
    pendingTabChange: string | null;
    onPendingTabResolved: () => void;
    onPendingTabCancelled: () => void;
}

export function ModelTab({
                             twin,
                             onUpdateModel,
                             onDirtyChange,
                             pendingTabChange,
                             onPendingTabResolved,
                             onPendingTabCancelled,
                         }: Props) {
    const [code, setCode] = useState(twin.model);
    const [isDirty, setIsDirty] = useState(false);
    const [modelErrorCount, setModelErrorCount] = useState(0);
    const [prompt, setPrompt] = useState("");
    const [showSaveDialog, setShowSaveDialog] = useState(false);
    const lspClientRef = useRef<MonacoLspClient | null>(null);
    const isDirtyRef = useRef(false);
    const documentUri = modelDocumentUri(twin.id);
    const hasModelErrors = modelErrorCount > 0;

    // If parent requests a tab change while model is already clean, resolve it immediately.
    useEffect(() => {
        if (pendingTabChange && !isDirty) {
            onPendingTabResolved();
        }
    }, [pendingTabChange, isDirty, onPendingTabResolved]);

    const handleEditorChange = (value: string | undefined) => {
        const v = value ?? "";
        setCode(v);
        const dirty = v !== twin.model;
        isDirtyRef.current = dirty;
        setIsDirty(dirty);
        onDirtyChange(dirty);
    };

    const handleSave = (newVersion: string) => {
        if (hasModelErrors) return;
        onUpdateModel(code, newVersion);
        isDirtyRef.current = false;
        setIsDirty(false);
        onDirtyChange(false);
        setShowSaveDialog(false);
        onPendingTabResolved();
    };

    const handleDiscard = () => {
        setCode(twin.model);
        isDirtyRef.current = false;
        setIsDirty(false);
        onDirtyChange(false);
        setShowSaveDialog(false);
        onPendingTabResolved();
    };

    const handleCancelDialog = () => {
        // Stay on model tab
        setShowSaveDialog(false);
        onPendingTabCancelled();
    };

    const handleEditorMount = (editor: any, monaco: any) => {
        const model = editor.getModel();
        if (!model) return;
        ensureTaraLanguage(monaco);
        monaco.editor.setTheme(TARA_THEME_ID);
        monaco.editor.setModelLanguage(model, TARA_LANGUAGE_ID);
        editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.KeyS, () => {
            if (!isDirtyRef.current) return;
            setShowSaveDialog(true);
        });
        lspClientRef.current?.dispose();
        const client = new MonacoLspClient(monaco, model, documentUri, setModelErrorCount);
        client.start(model.getValue());
        lspClientRef.current = client;
    };

    useEffect(() => {
        lspClientRef.current?.sync(code);
    }, [code]);

    useEffect(() => {
        return () => {
            lspClientRef.current?.dispose();
            lspClientRef.current = null;
            setModelErrorCount(0);
        };
    }, []);

    return (
        <div className="flex flex-col h-full min-h-0 gap-0 bg-[#0f1117]">
            {/* Dirty indicator */}
            {isDirty && (
                <div className="px-4 sm:px-6 lg:px-8 pt-4 flex-shrink-0">
                    <div
                        className="max-w-5xl mx-auto flex items-center gap-2 px-4 py-2 bg-amber-500/10 border border-amber-500/20 rounded-xl text-amber-400 text-xs">
                        <AlertTriangle className="w-3.5 h-3.5"/>
                        Unsaved changes — save a new version before leaving this tab
                        {hasModelErrors && (
                            <span className="text-red-300 ml-2">
                                Fix {modelErrorCount} error{modelErrorCount === 1 ? "" : "s"} to enable save
                            </span>
                        )}
                        <button
                            onClick={() => setShowSaveDialog(true)}
                            disabled={hasModelErrors}
                            className="ml-auto flex items-center gap-1.5 bg-amber-500/20 hover:bg-amber-500/30 disabled:bg-amber-500/10 disabled:text-amber-300/40 disabled:cursor-not-allowed border border-amber-500/30 px-3 py-1 rounded-lg transition-colors"
                            style={{fontWeight: 500}}
                        >
                            <Save className="w-3 h-3"/>
                            Save version
                        </button>
                    </div>
                </div>
            )}

            <div className="flex-1 min-h-0 px-4 sm:px-6 lg:px-8 py-4">
                <div className="h-full min-h-0 max-w-5xl mx-auto flex flex-col gap-4">
                    {/* Monaco editor */}
                    <div
                        className="flex-1 min-h-[320px] bg-[#1a1d27] border border-white/10 rounded-2xl overflow-hidden shadow-xl shadow-black/25">
                        <Editor
                            height="100%"
                            defaultLanguage={TARA_LANGUAGE_ID}
                            path={documentUri}
                            value={code}
                            onChange={handleEditorChange}
                            onMount={handleEditorMount}
                            theme={TARA_THEME_ID}
                            options={{
                                fontSize: 13,
                                lineHeight: 20,
                                fontFamily: "'JetBrains Mono', 'Fira Code', 'Cascadia Code', monospace",
                                minimap: {enabled: false},
                                insertSpaces: false,
                                tabSize: 4,
                                scrollBeyondLastLine: false,
                                detectIndentation: false,
                                suggestOnTriggerCharacters: true,
                                quickSuggestions: true,
                                wordBasedSuggestions: "off",
                                autoClosingBrackets: "always",
                                autoClosingQuotes: "always",
                                autoSurround: "languageDefined",
                                parameterHints: {enabled: true},
                                "semanticHighlighting.enabled": true,
                                padding: {top: 16, bottom: 16},
                                wordWrap: "on",
                                renderLineHighlight: "gutter",
                                lineNumbers: "on",
                                glyphMargin: false,
                                folding: true,
                                automaticLayout: true,
                            }}
                        />
                    </div>

                    {/* Prompt bar */}
                    <div className="flex-shrink-0 bg-[#1a1d27] border border-white/10 rounded-2xl px-4 py-3">
                        <div className="flex items-center gap-2">
                            <div className="flex-1 relative">
                                <input
                                    type="text"
                                    placeholder="Ask AI to modify the model… (e.g. 'Add a temperature sensor to the motor')"
                                    value={prompt}
                                    onChange={(e) => setPrompt(e.target.value)}
                                    onKeyDown={(e) => {
                                        if (e.key === "Enter" && prompt.trim()) setPrompt("");
                                    }}
                                    className="w-full bg-white/5 border border-white/10 rounded-xl px-4 py-2.5 text-white placeholder-white/25 text-sm outline-none focus:border-cyan-500/50 focus:ring-1 focus:ring-cyan-500/20 transition-all"
                                />
                            </div>
                            <button
                                disabled={!prompt.trim()}
                                className="flex items-center gap-2 bg-cyan-500 hover:bg-cyan-400 disabled:opacity-30 disabled:cursor-not-allowed text-white px-3.5 py-2.5 rounded-xl text-sm transition-all"
                            >
                                <Send className="w-4 h-4"/>
                                <span className="hidden sm:inline">Send</span>
                            </button>
                        </div>
                        <p className="text-white/20 text-xs mt-1.5 pl-1">AI model editing is coming soon</p>
                    </div>
                </div>
            </div>

            {/* Save dialog */}
            {showSaveDialog && (
                <SaveDialog
                    currentVersion={twin.version}
                    canSave={!hasModelErrors}
                    saveDisabledReason={`Fix ${modelErrorCount} model error${modelErrorCount === 1 ? "" : "s"} before saving.`}
                    onSave={handleSave}
                    onDiscard={handleDiscard}
                    onCancel={handleCancelDialog}
                />
            )}
        </div>
    );
}
