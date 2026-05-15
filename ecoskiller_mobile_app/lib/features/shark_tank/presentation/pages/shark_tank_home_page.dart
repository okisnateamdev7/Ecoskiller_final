import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:lucide_icons/lucide_icons.dart';
import 'package:animate_do/animate_do.dart';
import 'package:ecoskiller_mobile_app/core/theme/app_theme.dart';
import '../widgets/round_card.dart';
import 'idea_submission_wizard.dart';

class SharkTankHomePage extends StatefulWidget {
  final Map<String, dynamic> user;
  const SharkTankHomePage({super.key, required this.user});

  @override
  State<SharkTankHomePage> createState() => _SharkTankHomePageState();
}

class _SharkTankHomePageState extends State<SharkTankHomePage> {
  @override
  Widget build(BuildContext context) {
    bool isDark = Theme.of(context).brightness == Brightness.dark;

    return Scaffold(
      backgroundColor: isDark ? AppTheme.midnightAbyss : AppTheme.skyWhite,
      body: CustomScrollView(
        slivers: [
          _buildAppBar(context),
          SliverToBoxAdapter(
            child: Padding(
              padding: const EdgeInsets.all(20.0),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  _buildMainCTA(context),
                  const SizedBox(height: 32),
                  if (widget.user['role'] == 'CANDIDATE') ...[
                    _buildSectionHeader('TARGETED PITCHING'),
                    const SizedBox(height: 16),
                    _buildTargetedPitchingCard(),
                    const SizedBox(height: 32),
                  ],
                  _buildSectionHeader('ACTIVE ROUNDS'),
                  const SizedBox(height: 16),
                  _buildActiveRounds(),
                  const SizedBox(height: 32),
                  _buildSectionHeader('HALL OF FAME'),
                  const SizedBox(height: 16),
                  _buildLeaderboardPreview(),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildTargetedPitchingCard() {
    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: AppTheme.linkedinBlue.withOpacity(0.05),
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: AppTheme.linkedinBlue.withOpacity(0.1)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              const Icon(LucideIcons.target, color: Colors.redAccent, size: 20),
              const SizedBox(width: 12),
              Text(
                'DIRECT PITCH TO SHARKS',
                style: GoogleFonts.inter(fontWeight: FontWeight.bold, fontSize: 14),
              ),
            ],
          ),
          const SizedBox(height: 12),
          Text(
            'Target specific recruiters or corporates. Skip the general pool.',
            style: GoogleFonts.inter(fontSize: 12, color: Colors.grey),
          ),
          const SizedBox(height: 16),
          Wrap(
            spacing: 8,
            children: [
              _buildSharkChip('Google'),
              _buildSharkChip('Meta'),
              _buildSharkChip('Amazon'),
              _buildSharkChip('+12 More'),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildSharkChip(String label) {
    return Chip(
      label: Text(label, style: const TextStyle(fontSize: 10)),
      backgroundColor: Colors.white.withOpacity(0.05),
      labelStyle: const TextStyle(color: AppTheme.linkedinBlue),
      padding: EdgeInsets.zero,
    );
  }

  Widget _buildAppBar(BuildContext context) {
    bool isDark = Theme.of(context).brightness == Brightness.dark;
    return SliverAppBar(
      expandedHeight: 150,
      pinned: true,
      backgroundColor: isDark ? AppTheme.midnightAbyss : AppTheme.skyWhite,
      flexibleSpace: FlexibleSpaceBar(
        title: Text(
          'SHARK TANK NODE',
          style: GoogleFonts.inter(
            color: isDark ? AppTheme.crispText : AppTheme.deepOcean,
            fontWeight: FontWeight.w900,
            fontSize: 16,
            letterSpacing: 2,
          ),
        ),
        background: Stack(
          children: [
            Positioned.fill(
              child: Opacity(
                opacity: 0.1,
                child: Icon(LucideIcons.waves, size: 200, color: AppTheme.linkedinBlue),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildMainCTA(BuildContext context) {
    return FadeInUp(
      child: Container(
        padding: const EdgeInsets.all(24),
        decoration: BoxDecoration(
          gradient: const LinearGradient(
            colors: [Color(0xFF00416A), Color(0xFFE4E5E6)],
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
          ),
          borderRadius: BorderRadius.circular(24),
          boxShadow: [
            BoxShadow(
              color: Colors.blue.withOpacity(0.3),
              blurRadius: 20,
              offset: const Offset(0, 10),
            ),
          ],
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                const Icon(LucideIcons.lightbulb, color: Colors.amber, size: 32),
                const SizedBox(width: 12),
                Text(
                  'GOT AN IDEA?',
                  style: GoogleFonts.inter(
                    color: Colors.white,
                    fontWeight: FontWeight.w900,
                    fontSize: 20,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 12),
            Text(
              'Present your project to the world\'s top mentors & investors. Turn your vision into reality.',
              style: GoogleFonts.inter(color: Colors.white70, fontSize: 14),
            ),
            const SizedBox(height: 24),
            ElevatedButton(
              onPressed: () {
                Navigator.push(
                  context,
                  MaterialPageRoute(
                    builder: (context) => IdeaSubmissionWizard(user: widget.user),
                  ),
                );
              },
              style: ElevatedButton.styleFrom(
                backgroundColor: AppTheme.linkedinBlue,
                foregroundColor: Colors.white,
                padding: const EdgeInsets.symmetric(horizontal: 32, vertical: 16),
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                elevation: 0,
              ),
              child: Text(
                'SUBMIT YOUR IDEA',
                style: GoogleFonts.inter(fontWeight: FontWeight.bold, letterSpacing: 1),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildSectionHeader(String title) {
    return Text(
      title,
      style: GoogleFonts.inter(
        color: AppTheme.linkedinBlue.withOpacity(0.6),
        fontSize: 12,
        fontWeight: FontWeight.w900,
        letterSpacing: 2,
      ),
    );
  }

  Widget _buildActiveRounds() {
    return Column(
      children: [
        const RoundCard(
          title: 'Future Tech 2024',
          domain: 'Artificial Intelligence',
          prizePool: '₹5,00,000',
          deadline: '12 Days Left',
          color: Colors.blueAccent,
        ),
        const SizedBox(height: 16),
        const RoundCard(
          title: 'Eco-Warriors Pitch',
          domain: 'Sustainability',
          prizePool: '₹2,50,000',
          deadline: '5 Days Left',
          color: Colors.greenAccent,
        ),
      ],
    );
  }

  Widget _buildLeaderboardPreview() {
    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: AppTheme.linkedinBlue.withOpacity(0.05),
        borderRadius: BorderRadius.circular(24),
      ),
      child: Column(
        children: [
          _buildLeaderboardItem(1, 'Aryan Sharma', 'AI Smart Bin', '98.5'),
          const Divider(),
          _buildLeaderboardItem(2, 'Sarah Khan', 'Solar Backpack', '96.2'),
          const Divider(),
          _buildLeaderboardItem(3, 'James Wilson', 'EduConnect App', '94.8'),
        ],
      ),
    );
  }

  Widget _buildLeaderboardItem(int rank, String name, String project, String score) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 8.0),
      child: Row(
        children: [
          CircleAvatar(
            radius: 14,
            backgroundColor: rank == 1 ? Colors.amber : Colors.grey[300],
            child: Text(rank.toString(), style: const TextStyle(fontSize: 12, color: Colors.black)),
          ),
          const SizedBox(width: 16),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(name, style: GoogleFonts.inter(fontWeight: FontWeight.bold)),
                Text(project, style: GoogleFonts.inter(fontSize: 12, color: Colors.grey)),
              ],
            ),
          ),
          Text(score, style: GoogleFonts.inter(fontWeight: FontWeight.w900, color: AppTheme.linkedinBlue)),
        ],
      ),
    );
  }
}
