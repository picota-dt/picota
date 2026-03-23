import {useEffect, useState} from "react";
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

// ─── Save dialog ──────────────────────────────────────────────────────────────

interface SaveDialogProps {
    currentVersion: string;
    onSave: (newVersion: string) => void;
    onDiscard: () => void;
    onCancel?: () => void;
}

function SaveDialog({currentVersion, onSave, onDiscard, onCancel}: SaveDialogProps) {
    const [selected, setSelected] = useState<"patch" | "minor" | "major">("patch");
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

                    <div className="flex gap-3 pt-2">
                        <button
                            onClick={onDiscard}
                            className="flex-1 py-2.5 rounded-xl border border-white/10 text-white/40 hover:text-white/60 text-sm transition-colors"
                        >
                            Discard changes
                        </button>
                        <button
                            onClick={() => onSave(bumpVersion(currentVersion, selected))}
                            className="flex-1 py-2.5 rounded-xl bg-cyan-500 hover:bg-cyan-400 text-white text-sm transition-colors shadow-lg shadow-cyan-500/20"
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
}

export function ModelTab({twin, onUpdateModel, onDirtyChange, pendingTabChange, onPendingTabResolved}: Props) {
    const [code, setCode] = useState(twin.model);
    const [isDirty, setIsDirty] = useState(false);
    const [prompt, setPrompt] = useState("");
    const [showSaveDialog, setShowSaveDialog] = useState(false);

    // When parent signals a pending tab change and we're dirty, show save dialog
    useEffect(() => {
        if (pendingTabChange && isDirty) {
            setShowSaveDialog(true);
        } else if (pendingTabChange && !isDirty) {
            onPendingTabResolved();
        }
    }, [pendingTabChange]);

    const handleEditorChange = (value: string | undefined) => {
        const v = value ?? "";
        setCode(v);
        const dirty = v !== twin.model;
        setIsDirty(dirty);
        onDirtyChange(dirty);
    };

    const handleSave = (newVersion: string) => {
        onUpdateModel(code, newVersion);
        setIsDirty(false);
        onDirtyChange(false);
        setShowSaveDialog(false);
        onPendingTabResolved();
    };

    const handleDiscard = () => {
        setCode(twin.model);
        setIsDirty(false);
        onDirtyChange(false);
        setShowSaveDialog(false);
        onPendingTabResolved();
    };

    const handleCancelDialog = () => {
        // Stay on model tab
        setShowSaveDialog(false);
        onPendingTabResolved(); // tell parent we handled it (no navigation)
    };

    return (
        <div className="flex flex-col h-full min-h-0 gap-0 bg-[#0f1117]">
            {/* Dirty indicator */}
            {isDirty && (
                <div
                    className="flex items-center gap-2 px-4 py-2 bg-amber-500/10 border-b border-amber-500/20 text-amber-400 text-xs flex-shrink-0">
                    <AlertTriangle className="w-3.5 h-3.5"/>
                    Unsaved changes — save a new version before leaving this tab
                    <button
                        onClick={() => setShowSaveDialog(true)}
                        className="ml-auto flex items-center gap-1.5 bg-amber-500/20 hover:bg-amber-500/30 border border-amber-500/30 px-3 py-1 rounded-lg transition-colors"
                        style={{fontWeight: 500}}
                    >
                        <Save className="w-3 h-3"/>
                        Save version
                    </button>
                </div>
            )}

            <div className="flex-1 min-h-0 px-4 sm:px-6 lg:px-8 py-4">
                <div className="h-full min-h-0 max-w-5xl mx-auto flex flex-col gap-4">
                    {/* Monaco editor */}
                    <div
                        className="flex-1 min-h-[320px] bg-[#1a1d27] border border-white/10 rounded-2xl overflow-hidden shadow-xl shadow-black/25">
                        <Editor
                            height="100%"
                            defaultLanguage="yaml"
                            value={code}
                            onChange={handleEditorChange}
                            theme="vs-dark"
                            options={{
                                fontSize: 13,
                                lineHeight: 20,
                                fontFamily: "'JetBrains Mono', 'Fira Code', 'Cascadia Code', monospace",
                                minimap: {enabled: false},
                                scrollBeyondLastLine: false,
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
                    onSave={handleSave}
                    onDiscard={handleDiscard}
                    onCancel={handleCancelDialog}
                />
            )}
        </div>
    );
}
