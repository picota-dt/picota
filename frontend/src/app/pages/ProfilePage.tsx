import {useEffect, useState} from "react";
import {useApp} from "../context/AppContext";
import {useNavigate} from "react-router";
import {Bell, Calendar, Check, Coins, Key, Mail, Plus, ShieldCheck, Trash2, User} from "lucide-react";

const CREDIT_PACKS = [
    {credits: 500, price: "$9", label: "Starter"},
    {credits: 2000, price: "$29", label: "Growth", popular: true},
    {credits: 10000, price: "$99", label: "Scale"},
];

export default function ProfilePage() {
    const navigate = useNavigate();
    const {user, updateProfile, changePassword, deleteAccount} = useApp();
    const [bought, setBought] = useState<number | null>(null);
    const [showBuyModal, setShowBuyModal] = useState(false);
    const [saving, setSaving] = useState(false);
    const [saved, setSaved] = useState(false);
    const [saveError, setSaveError] = useState("");
    const [showPasswordModal, setShowPasswordModal] = useState(false);
    const [currentPassword, setCurrentPassword] = useState("");
    const [newPassword, setNewPassword] = useState("");
    const [confirmPassword, setConfirmPassword] = useState("");
    const [passwordSaving, setPasswordSaving] = useState(false);
    const [passwordError, setPasswordError] = useState("");
    const [showDeleteModal, setShowDeleteModal] = useState(false);
    const [deleting, setDeleting] = useState(false);
    const [deleteError, setDeleteError] = useState("");
    const [fullName, setFullName] = useState(user.name);
    const [email, setEmail] = useState(user.email);

    useEffect(() => {
        setFullName(user.name);
        setEmail(user.email);
    }, [user.name, user.email]);

    const handleSave = async () => {
        const nextName = fullName.trim();
        const nextEmail = email.trim();
        if (!nextName || !nextEmail) {
            setSaveError("Name and email are required.");
            return;
        }
        setSaveError("");
        setSaving(true);
        try {
            await updateProfile({name: nextName, email: nextEmail});
            setSaved(true);
            setTimeout(() => setSaved(false), 2500);
        } catch (error) {
            setSaveError(error instanceof Error ? error.message : "Unable to save profile.");
        } finally {
            setSaving(false);
        }
    };

    const handleBuy = async (idx: number) => {
        setBought(idx);
        await new Promise((r) => setTimeout(r, 900));
        setBought(null);
        setShowBuyModal(false);
    };

    const handleChangePassword = async () => {
        if (!currentPassword || !newPassword || !confirmPassword) {
            setPasswordError("Please complete all password fields.");
            return;
        }
        if (newPassword.length < 8) {
            setPasswordError("Password must be at least 8 characters.");
            return;
        }
        if (newPassword !== confirmPassword) {
            setPasswordError("New password and confirmation do not match.");
            return;
        }
        setPasswordError("");
        setPasswordSaving(true);
        try {
            await changePassword(currentPassword, newPassword);
            setShowPasswordModal(false);
            setCurrentPassword("");
            setNewPassword("");
            setConfirmPassword("");
        } catch (error) {
            setPasswordError(error instanceof Error ? error.message : "Unable to change password.");
        } finally {
            setPasswordSaving(false);
        }
    };

    const handleDeleteAccount = async () => {
        setDeleteError("");
        setDeleting(true);
        try {
            await deleteAccount();
            navigate("/");
        } catch (error) {
            setDeleteError(error instanceof Error ? error.message : "Unable to delete account.");
        } finally {
            setDeleting(false);
        }
    };

    const memberSince = formatMemberSince(user.joinedAt);

    return (
        <div className="min-h-screen bg-[#0f1117] py-8 px-4">
            <div className="max-w-2xl mx-auto flex flex-col gap-5">

                {/* Header card */}
                <div className="bg-[#1a1d27] border border-white/8 rounded-2xl p-6 flex items-center gap-5">
                    <div
                        className="w-16 h-16 rounded-2xl bg-gradient-to-br from-cyan-400 to-blue-500 flex items-center justify-center text-white text-xl flex-shrink-0"
                        style={{fontWeight: 700}}>
                        {user.avatarInitials}
                    </div>
                    <div className="flex-1 min-w-0">
                        <h1 className="text-white" style={{fontWeight: 700, fontSize: "1.15rem"}}>{user.name}</h1>
                        <p className="text-white/40 text-sm">{user.email}</p>
                    </div>
                </div>

                {/* Credits card */}
                <div className="bg-[#1a1d27] border border-white/8 rounded-2xl p-6">
                    <div className="flex items-center justify-between mb-4">
                        <div className="flex items-center gap-2">
                            <Coins className="w-4 h-4 text-amber-400"/>
                            <h2 className="text-white" style={{fontWeight: 600}}>Credits</h2>
                        </div>
                        <button
                            onClick={() => setShowBuyModal(true)}
                            className="flex items-center gap-1.5 bg-amber-500/15 hover:bg-amber-500/25 border border-amber-500/30 text-amber-400 px-3.5 py-1.5 rounded-xl text-sm transition-all"
                            style={{fontWeight: 500}}
                        >
                            <Plus className="w-3.5 h-3.5"/>
                            Buy credits
                        </button>
                    </div>

                    <div className="flex items-end gap-2 mb-3">
                        <span className="text-white"
                              style={{fontWeight: 800, fontSize: "2.5rem"}}>{user.credits.toLocaleString()}</span>
                        <span className="text-white/40 text-sm mb-2">credits remaining</span>
                    </div>

                    {/* Visual bar */}
                    <div className="h-2 bg-white/6 rounded-full overflow-hidden">
                        <div
                            className="h-full bg-gradient-to-r from-amber-400 to-amber-500 rounded-full"
                            style={{width: `${Math.min((user.credits / 5000) * 100, 100)}%`}}
                        />
                    </div>
                    <p className="text-white/25 text-xs mt-2">Credits are consumed when active twins run inference</p>
                </div>

                {/* Profile info */}
                <div className="bg-[#1a1d27] border border-white/8 rounded-2xl p-6">
                    <h2 className="text-white mb-5" style={{fontWeight: 600}}>Profile information</h2>
                    <div className="flex flex-col gap-4">
                        <div>
                            <label
                                className="text-white/40 text-xs uppercase tracking-wider mb-1.5 flex items-center gap-1.5">
                                <User className="w-3.5 h-3.5"/>
                                Full name
                            </label>
                            <input
                                value={fullName}
                                onChange={(event) => setFullName(event.target.value)}
                                placeholder={user.name}
                                className="w-full bg-white/5 border border-white/10 rounded-xl px-3.5 py-2.5 text-white text-sm outline-none focus:border-cyan-500/60 focus:ring-1 focus:ring-cyan-500/20 transition-all"
                            />
                        </div>
                        <div>
                            <label
                                className="text-white/40 text-xs uppercase tracking-wider mb-1.5 flex items-center gap-1.5">
                                <Mail className="w-3.5 h-3.5"/>
                                Email address
                            </label>
                            <input
                                value={email}
                                onChange={(event) => setEmail(event.target.value)}
                                placeholder={user.email}
                                className="w-full bg-white/5 border border-white/10 rounded-xl px-3.5 py-2.5 text-white text-sm outline-none focus:border-cyan-500/60 focus:ring-1 focus:ring-cyan-500/20 transition-all"
                            />
                        </div>
                        <div>
                            <label
                                className="text-white/40 text-xs uppercase tracking-wider mb-1.5 flex items-center gap-1.5">
                                <Calendar className="w-3.5 h-3.5"/>
                                Member since
                            </label>
                            <p className="text-white/50 text-sm px-3.5 py-2.5">{memberSince}</p>
                        </div>
                    </div>
                    <button
                        onClick={handleSave}
                        disabled={saving}
                        className="mt-5 flex items-center gap-2 bg-cyan-500 hover:bg-cyan-400 disabled:opacity-50 text-white px-4 py-2.5 rounded-xl text-sm transition-all shadow-lg shadow-cyan-500/20"
                        style={{fontWeight: 500}}
                    >
                        {saved ? (
                            <><Check className="w-4 h-4"/> Saved</>
                        ) : saving ? (
                            "Saving…"
                        ) : (
                            "Save changes"
                        )}
                    </button>
                    {saveError && <p className="text-red-400 text-sm mt-3">{saveError}</p>}
                </div>

                {/* Security & preferences */}
                <div className="bg-[#1a1d27] border border-white/8 rounded-2xl p-6">
                    <h2 className="text-white mb-4" style={{fontWeight: 600}}>Security & preferences</h2>
                    <div className="flex flex-col gap-2">
                        <button
                            onClick={() => {
                                setPasswordError("");
                                setShowPasswordModal(true);
                            }}
                            className="flex items-center gap-3 w-full text-left px-4 py-3 rounded-xl hover:bg-white/5 transition-colors group"
                        >
                            <div
                                className="w-8 h-8 rounded-lg bg-white/6 flex items-center justify-center flex-shrink-0">
                                <Key className="w-4 h-4 text-white/40 group-hover:text-white/60 transition-colors"/>
                            </div>
                            <div className="flex-1 min-w-0">
                                <p className="text-white/70 text-sm group-hover:text-white transition-colors"
                                   style={{fontWeight: 500}}>Change password</p>
                                <p className="text-white/30 text-xs">Update your login credentials</p>
                            </div>
                        </button>
                        <button
                            className="flex items-center gap-3 w-full text-left px-4 py-3 rounded-xl hover:bg-white/5 transition-colors group"
                        >
                            <div
                                className="w-8 h-8 rounded-lg bg-white/6 flex items-center justify-center flex-shrink-0">
                                <ShieldCheck
                                    className="w-4 h-4 text-white/40 group-hover:text-white/60 transition-colors"/>
                            </div>
                            <div className="flex-1 min-w-0">
                                <p className="text-white/70 text-sm group-hover:text-white transition-colors"
                                   style={{fontWeight: 500}}>Two-factor authentication</p>
                                <p className="text-white/30 text-xs">Not enabled</p>
                            </div>
                        </button>
                        <button
                            className="flex items-center gap-3 w-full text-left px-4 py-3 rounded-xl hover:bg-white/5 transition-colors group"
                        >
                            <div
                                className="w-8 h-8 rounded-lg bg-white/6 flex items-center justify-center flex-shrink-0">
                                <Bell className="w-4 h-4 text-white/40 group-hover:text-white/60 transition-colors"/>
                            </div>
                            <div className="flex-1 min-w-0">
                                <p className="text-white/70 text-sm group-hover:text-white transition-colors"
                                   style={{fontWeight: 500}}>Notification preferences</p>
                                <p className="text-white/30 text-xs">Email · In-app</p>
                            </div>
                        </button>
                    </div>
                </div>

                {/* Danger zone */}
                <div className="bg-red-500/5 border border-red-500/15 rounded-2xl p-6">
                    <h2 className="text-red-400 mb-1" style={{fontWeight: 600}}>Danger zone</h2>
                    <p className="text-white/30 text-sm mb-4">This action is permanent and cannot be undone.</p>
                    <button
                        onClick={() => {
                            setDeleteError("");
                            setShowDeleteModal(true);
                        }}
                        className="flex items-center gap-2 bg-red-500/10 hover:bg-red-500/20 border border-red-500/20 text-red-400 px-4 py-2.5 rounded-xl text-sm transition-all">
                        <Trash2 className="w-4 h-4"/>
                        Delete account
                    </button>
                </div>
            </div>

            {/* Change password modal */}
            {showPasswordModal && (
                <div
                    className="fixed inset-0 z-50 flex items-center justify-center p-4"
                    onClick={(event) => {
                        if (event.target === event.currentTarget) setShowPasswordModal(false);
                    }}
                >
                    <div className="absolute inset-0 bg-black/60 backdrop-blur-sm"/>
                    <div
                        className="relative bg-[#1a1d27] border border-white/10 rounded-2xl p-6 w-full max-w-sm shadow-2xl">
                        <div className="flex items-center gap-2 mb-5">
                            <Key className="w-5 h-5 text-cyan-400"/>
                            <h2 className="text-white" style={{fontWeight: 600}}>Change password</h2>
                        </div>

                        <div className="flex flex-col gap-3">
                            <input
                                type="password"
                                value={currentPassword}
                                onChange={(event) => setCurrentPassword(event.target.value)}
                                placeholder="Current password"
                                className="w-full bg-white/5 border border-white/10 rounded-xl px-3.5 py-2.5 text-white text-sm outline-none focus:border-cyan-500/60 focus:ring-1 focus:ring-cyan-500/20 transition-all"
                            />
                            <input
                                type="password"
                                value={newPassword}
                                onChange={(event) => setNewPassword(event.target.value)}
                                placeholder="New password"
                                className="w-full bg-white/5 border border-white/10 rounded-xl px-3.5 py-2.5 text-white text-sm outline-none focus:border-cyan-500/60 focus:ring-1 focus:ring-cyan-500/20 transition-all"
                            />
                            <input
                                type="password"
                                value={confirmPassword}
                                onChange={(event) => setConfirmPassword(event.target.value)}
                                placeholder="Confirm new password"
                                className="w-full bg-white/5 border border-white/10 rounded-xl px-3.5 py-2.5 text-white text-sm outline-none focus:border-cyan-500/60 focus:ring-1 focus:ring-cyan-500/20 transition-all"
                            />
                        </div>

                        {passwordError && <p className="text-red-400 text-sm mt-3">{passwordError}</p>}

                        <div className="flex gap-3 mt-5">
                            <button
                                onClick={() => setShowPasswordModal(false)}
                                className="flex-1 py-2.5 rounded-xl border border-white/10 text-white/40 hover:text-white/60 text-sm transition-colors"
                            >
                                Cancel
                            </button>
                            <button
                                onClick={handleChangePassword}
                                disabled={passwordSaving}
                                className="flex-1 py-2.5 rounded-xl bg-cyan-500 hover:bg-cyan-400 disabled:opacity-50 text-white text-sm transition-colors"
                                style={{fontWeight: 500}}
                            >
                                {passwordSaving ? "Updating…" : "Update password"}
                            </button>
                        </div>
                    </div>
                </div>
            )}

            {/* Delete account modal */}
            {showDeleteModal && (
                <div
                    className="fixed inset-0 z-50 flex items-center justify-center p-4"
                    onClick={(event) => {
                        if (event.target === event.currentTarget) setShowDeleteModal(false);
                    }}
                >
                    <div className="absolute inset-0 bg-black/60 backdrop-blur-sm"/>
                    <div
                        className="relative bg-[#1a1d27] border border-red-500/20 rounded-2xl p-6 w-full max-w-sm shadow-2xl">
                        <div className="flex items-center gap-2 mb-3">
                            <Trash2 className="w-5 h-5 text-red-400"/>
                            <h2 className="text-white" style={{fontWeight: 600}}>Delete account</h2>
                        </div>
                        <p className="text-white/35 text-sm">
                            This will permanently delete your account and all your data.
                        </p>
                        {deleteError && <p className="text-red-400 text-sm mt-3">{deleteError}</p>}
                        <div className="flex gap-3 mt-5">
                            <button
                                onClick={() => setShowDeleteModal(false)}
                                className="flex-1 py-2.5 rounded-xl border border-white/10 text-white/40 hover:text-white/60 text-sm transition-colors"
                            >
                                Cancel
                            </button>
                            <button
                                onClick={handleDeleteAccount}
                                disabled={deleting}
                                className="flex-1 py-2.5 rounded-xl bg-red-500/20 hover:bg-red-500/30 border border-red-500/25 disabled:opacity-50 text-red-400 text-sm transition-colors"
                                style={{fontWeight: 500}}
                            >
                                {deleting ? "Deleting…" : "Delete account"}
                            </button>
                        </div>
                    </div>
                </div>
            )}

            {/* Buy credits modal */}
            {showBuyModal && (
                <div
                    className="fixed inset-0 z-50 flex items-center justify-center p-4"
                    onClick={(e) => {
                        if (e.target === e.currentTarget) setShowBuyModal(false);
                    }}
                >
                    <div className="absolute inset-0 bg-black/60 backdrop-blur-sm"/>
                    <div
                        className="relative bg-[#1a1d27] border border-white/10 rounded-2xl p-6 w-full max-w-sm shadow-2xl">
                        <div className="flex items-center gap-2 mb-5">
                            <Coins className="w-5 h-5 text-amber-400"/>
                            <h2 className="text-white" style={{fontWeight: 600}}>Buy credits</h2>
                        </div>
                        <div className="flex flex-col gap-3">
                            {CREDIT_PACKS.map((pack, idx) => (
                                <button
                                    key={pack.label}
                                    onClick={() => handleBuy(idx)}
                                    disabled={bought !== null}
                                    className={`relative flex items-center justify-between px-4 py-3.5 rounded-xl border transition-all ${
                                        pack.popular
                                            ? "bg-cyan-500/15 border-cyan-500/40 hover:bg-cyan-500/25"
                                            : "bg-white/4 border-white/10 hover:border-white/20"
                                    }`}
                                >
                                    {pack.popular && (
                                        <span
                                            className="absolute -top-2 left-4 bg-cyan-500 text-white text-xs px-2 py-0.5 rounded-full"
                                            style={{fontWeight: 600}}>
                      Most popular
                    </span>
                                    )}
                                    <div className="text-left">
                                        <p className="text-white text-sm" style={{fontWeight: 600}}>{pack.label}</p>
                                        <p className="text-white/40 text-xs">{pack.credits.toLocaleString()} credits</p>
                                    </div>
                                    <div className="flex items-center gap-2">
                                        {bought === idx ? (
                                            <span className="text-cyan-400 text-sm">Processing…</span>
                                        ) : (
                                            <span className="text-white"
                                                  style={{fontWeight: 700, fontSize: "1.1rem"}}>{pack.price}</span>
                                        )}
                                    </div>
                                </button>
                            ))}
                        </div>
                        <button
                            onClick={() => setShowBuyModal(false)}
                            className="w-full mt-4 py-2 text-white/30 hover:text-white/50 text-sm transition-colors"
                        >
                            Cancel
                        </button>
                    </div>
                </div>
            )}
        </div>
    );
}

function formatMemberSince(value: string) {
    if (!value || value.trim() === "" || value === "-") return "-";
    const parsed = new Date(value);
    if (Number.isNaN(parsed.getTime())) return value;
    return new Intl.DateTimeFormat(undefined, {
        year: "numeric",
        month: "long",
        day: "numeric",
    }).format(parsed);
}
