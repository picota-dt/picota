import {useState} from "react";
import {useNavigate} from "react-router";
import {DigitalTwin, TwinStatus, useApp} from "../../context/AppContext";
import {Check, Coins, FileText, Hash, Info, Power, Save, Tag, Trash2} from "lucide-react";

const STATUS_OPTIONS: { value: TwinStatus; label: string; dot: string; text: string }[] = [
    {value: "active", label: "Active", dot: "bg-emerald-400", text: "text-emerald-400"},
    {value: "draft", label: "Draft", dot: "bg-amber-400", text: "text-amber-400"},
    {value: "offline", label: "Offline", dot: "bg-white/25", text: "text-white/40"},
];

interface Props {
    twin: DigitalTwin;
}

function hasDefinedModel(model: string): boolean {
    const content = model?.trim();
    if (!content) return false;

    const nonCommentLines = content
        .split(/\r?\n/)
        .map((line) => line.trim())
        .filter((line) => line.length > 0 && !line.startsWith("#"));

    const looksLikeDefaultTemplate =
        nonCommentLines.length === 2 &&
        nonCommentLines.includes("subjects: []") &&
        nonCommentLines.includes("constraints: []");

    return !looksLikeDefaultTemplate;
}

export function PropertiesTab({twin}: Props) {
    const navigate = useNavigate();
    const {updateTwin, deleteTwin} = useApp();
    const [name, setName] = useState(twin.name);
    const [description, setDescription] = useState(twin.description);
    const [saving, setSaving] = useState(false);
    const [saved, setSaved] = useState(false);
    const [showDeleteModal, setShowDeleteModal] = useState(false);
    const [deleteConfirmName, setDeleteConfirmName] = useState("");
    const [deleteError, setDeleteError] = useState("");
    const [deleting, setDeleting] = useState(false);

    const currentStatus = STATUS_OPTIONS.find((s) => s.value === twin.status)!;
    const isActive = twin.status === "active";
    const canActivate = hasDefinedModel(twin.model);

    const handleToggleActive = () => {
        if (!isActive && !canActivate) return;
        const next: TwinStatus = isActive ? "offline" : "active";
        updateTwin(twin.id, {status: next});
    };

    const handleSave = async () => {
        setSaving(true);
        await new Promise((r) => setTimeout(r, 600));
        updateTwin(twin.id, {name, description});
        setSaving(false);
        setSaved(true);
        setTimeout(() => setSaved(false), 2500);
    };

    const handleDeleteTwin = async () => {
        if (deleteConfirmName.trim() !== twin.name.trim()) {
            setDeleteError("Type the twin name exactly to confirm deletion.");
            return;
        }
        setDeleting(true);
        setDeleteError("");
        try {
            await deleteTwin(twin.id);
            setShowDeleteModal(false);
            navigate("/twins");
        } catch (error: any) {
            setDeleteError(String(error?.message ?? "Unable to delete digital twin"));
        } finally {
            setDeleting(false);
        }
    };

    return (
        <div className="flex flex-col gap-5 max-w-2xl">
            {/* Activate / deactivate banner */}
            <div className={`flex items-center justify-between p-4 rounded-xl border ${
                isActive
                    ? "bg-emerald-500/8 border-emerald-500/20"
                    : "bg-white/4 border-white/8"
            }`}>
                <div className="flex items-center gap-3">
                    <span className={`w-2 h-2 rounded-full ${currentStatus.dot} ${isActive ? "animate-pulse" : ""}`}/>
                    <div>
                        <p className={`text-sm ${currentStatus.text}`} style={{fontWeight: 600}}>
                            {currentStatus.label}
                        </p>
                        <p className="text-white/30 text-xs">
                            {isActive ? "Twin is running and consuming credits" : "Twin is not active"}
                        </p>
                        {!isActive && !canActivate && (
                            <p className="text-amber-400/80 text-xs mt-1">
                                Define and save a model before activation.
                            </p>
                        )}
                    </div>
                </div>
                <button
                    onClick={handleToggleActive}
                    disabled={!isActive && !canActivate}
                    className={`flex items-center gap-2 px-4 py-2 rounded-xl text-sm transition-all ${
                        isActive
                            ? "bg-red-500/10 hover:bg-red-500/20 border border-red-500/25 text-red-400"
                            : "bg-emerald-500/15 hover:bg-emerald-500/25 border border-emerald-500/25 text-emerald-400 disabled:bg-white/6 disabled:border-white/10 disabled:text-white/30 disabled:cursor-not-allowed disabled:hover:bg-white/6"
                    }`}
                    style={{fontWeight: 500}}
                >
                    <Power className="w-3.5 h-3.5"/>
                    {isActive ? "Deactivate" : "Activate"}
                </button>
            </div>

            {/* Metadata fields */}
            <div className="bg-[#1a1d27] border border-white/8 rounded-2xl p-5 flex flex-col gap-4">
                <h3 className="text-white/70 text-xs uppercase tracking-wider" style={{fontWeight: 600}}>
                    Metadata
                </h3>

                <div>
                    <label className="text-white/40 text-xs uppercase tracking-wider mb-1.5 flex items-center gap-1.5">
                        <FileText className="w-3.5 h-3.5"/> Name
                    </label>
                    <input
                        value={name}
                        onChange={(e) => setName(e.target.value)}
                        className="w-full bg-white/5 border border-white/10 rounded-xl px-3.5 py-2.5 text-white text-sm outline-none focus:border-cyan-500/60 focus:ring-1 focus:ring-cyan-500/20 transition-all"
                    />
                </div>

                <div>
                    <label className="text-white/40 text-xs uppercase tracking-wider mb-1.5 flex items-center gap-1.5">
                        <Info className="w-3.5 h-3.5"/> Description
                    </label>
                    <textarea
                        value={description}
                        onChange={(e) => setDescription(e.target.value)}
                        rows={3}
                        className="w-full bg-white/5 border border-white/10 rounded-xl px-3.5 py-2.5 text-white text-sm outline-none focus:border-cyan-500/60 focus:ring-1 focus:ring-cyan-500/20 transition-all resize-none"
                    />
                </div>

                <div className="grid grid-cols-2 gap-4">
                    <div>
                        <label
                            className="text-white/40 text-xs uppercase tracking-wider mb-1.5 flex items-center gap-1.5">
                            <Hash className="w-3.5 h-3.5"/> Version
                        </label>
                        <p className="text-white/60 text-sm px-3.5 py-2.5 bg-white/3 border border-white/6 rounded-xl">
                            v{twin.version}
                        </p>
                    </div>
                    <div>
                        <label
                            className="text-white/40 text-xs uppercase tracking-wider mb-1.5 flex items-center gap-1.5">
                            <Tag className="w-3.5 h-3.5"/> Asset type
                        </label>
                        <p className="text-white/60 text-sm px-3.5 py-2.5 bg-white/3 border border-white/6 rounded-xl">
                            {twin.type}
                        </p>
                    </div>
                </div>

                <button
                    onClick={handleSave}
                    disabled={saving}
                    className="self-start flex items-center gap-2 bg-cyan-500 hover:bg-cyan-400 disabled:opacity-50 text-white px-4 py-2.5 rounded-xl text-sm transition-all shadow-lg shadow-cyan-500/15"
                    style={{fontWeight: 500}}
                >
                    {saved ? <><Check className="w-4 h-4"/>Saved</> : saving ? "Saving…" : <><Save className="w-4 h-4"/>Save
                        changes</>}
                </button>
            </div>

            {/* Credits */}
            <div className="bg-[#1a1d27] border border-white/8 rounded-2xl p-5">
                <h3 className="text-white/70 text-xs uppercase tracking-wider mb-4" style={{fontWeight: 600}}>
                    Resource usage
                </h3>
                <div className="flex items-center gap-3">
                    <div
                        className="w-10 h-10 rounded-xl bg-amber-500/15 border border-amber-500/20 flex items-center justify-center">
                        <Coins className="w-5 h-5 text-amber-400"/>
                    </div>
                    <div>
                        <p className="text-white" style={{fontWeight: 700, fontSize: "1.3rem"}}>
                            {twin.creditsUsed.toLocaleString()}
                        </p>
                        <p className="text-white/35 text-xs">credits consumed by this twin</p>
                    </div>
                </div>
            </div>

            {/* Danger zone */}
            <div className="bg-red-500/5 border border-red-500/15 rounded-2xl p-5">
                <h3 className="text-red-400 text-xs uppercase tracking-wider mb-1" style={{fontWeight: 600}}>
                    Danger zone
                </h3>
                <p className="text-white/35 text-sm mb-4">
                    Deleting this digital twin is permanent. Model versions, datasets and related training jobs will be
                    removed.
                </p>
                <button
                    onClick={() => {
                        setDeleteError("");
                        setDeleteConfirmName("");
                        setShowDeleteModal(true);
                    }}
                    className="flex items-center gap-2 bg-red-500/10 hover:bg-red-500/20 border border-red-500/20 text-red-400 px-4 py-2.5 rounded-xl text-sm transition-all"
                >
                    <Trash2 className="w-4 h-4"/>
                    Delete digital twin
                </button>
            </div>

            {showDeleteModal && (
                <div
                    className="fixed inset-0 z-50 flex items-center justify-center p-4"
                    onClick={(event) => {
                        if (event.target !== event.currentTarget || deleting) return;
                        setShowDeleteModal(false);
                    }}
                >
                    <div className="absolute inset-0 bg-black/60 backdrop-blur-sm"/>
                    <div
                        className="relative bg-[#1a1d27] border border-red-500/20 rounded-2xl p-6 w-full max-w-md shadow-2xl">
                        <div className="flex items-center gap-2 mb-2">
                            <Trash2 className="w-5 h-5 text-red-400"/>
                            <h2 className="text-red-300" style={{fontWeight: 600}}>Delete digital twin</h2>
                        </div>
                        <p className="text-white/40 text-sm mb-4">
                            This action cannot be undone. Type <span
                            className="text-white/70 font-mono">{twin.name}</span> to confirm.
                        </p>
                        <input
                            value={deleteConfirmName}
                            onChange={(event) => setDeleteConfirmName(event.target.value)}
                            placeholder={twin.name}
                            className="w-full bg-white/5 border border-white/10 rounded-xl px-3.5 py-2.5 text-white text-sm outline-none focus:border-red-500/60 focus:ring-1 focus:ring-red-500/20 transition-all"
                        />
                        {deleteError && <p className="text-red-400 text-sm mt-3">{deleteError}</p>}
                        <div className="flex gap-3 mt-5">
                            <button
                                onClick={() => setShowDeleteModal(false)}
                                disabled={deleting}
                                className="flex-1 py-2.5 rounded-xl border border-white/10 text-white/40 hover:text-white/60 disabled:opacity-50 text-sm transition-colors"
                            >
                                Cancel
                            </button>
                            <button
                                onClick={handleDeleteTwin}
                                disabled={deleting}
                                className="flex-1 py-2.5 rounded-xl bg-red-500/20 hover:bg-red-500/30 border border-red-500/25 text-red-300 disabled:opacity-50 text-sm transition-colors"
                                style={{fontWeight: 500}}
                            >
                                {deleting ? "Deleting…" : "Delete permanently"}
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}
