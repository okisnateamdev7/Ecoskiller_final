import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:lucide_icons/lucide_icons.dart';
import 'package:animate_do/animate_do.dart';
import 'package:ecoskiller_mobile_app/core/theme/app_theme.dart';

class RecruiterSharkTankPage extends StatefulWidget {
  final Map<String, dynamic> user;
  const RecruiterSharkTankPage({super.key, required this.user});

  @override
  State<RecruiterSharkTankPage> createState() => _RecruiterSharkTankPageState();
}

class _RecruiterSharkTankPageState extends State<RecruiterSharkTankPage> {
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
                  _buildSectionHeader('TALENT SCOUTING'),
                  const SizedBox(height: 16),
                  _buildScoutingGrid(),
                  const SizedBox(height: 32),
                  _buildSectionHeader('IDEAS UNDER REVIEW'),
                  const SizedBox(height: 16),
                  _buildReviewList(),
                  const SizedBox(height: 32),
                  _buildSectionHeader('LIVE SESSIONS'),
                  const SizedBox(height: 16),
                  _buildLiveSessions(),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildAppBar(BuildContext context) {
    return SliverAppBar(
      expandedHeight: 120,
      pinned: true,
      title: Text(
        'SHARK PANEL',
        style: GoogleFonts.inter(fontWeight: FontWeight.w900, letterSpacing: 2, fontSize: 16),
      ),
    );
  }

  Widget _buildSectionHeader(String title) {
    return Text(
      title,
      style: GoogleFonts.inter(
        color: AppTheme.linkedinBlue.withOpacity(0.6),
        fontSize: 11,
        fontWeight: FontWeight.w900,
        letterSpacing: 2,
      ),
    );
  }

  Widget _buildScoutingGrid() {
    return GridView.count(
      shrinkWrap: true,
      physics: const NeverScrollableScrollPhysics(),
      crossAxisCount: 2,
      crossAxisSpacing: 16,
      mainAxisSpacing: 16,
      childAspectRatio: 1.2,
      children: [
        _ScoutCard(LucideIcons.userPlus, 'Shortlisted', '24', Colors.blue),
        _ScoutCard(LucideIcons.award, 'Top Talent', '8', Colors.purple),
      ],
    );
  }

  Widget _buildReviewList() {
    return Column(
      children: [
        _ReviewItem('Smart Home AI', 'Candidate: Aryan Sharma', 8.5),
        const SizedBox(height: 12),
        _ReviewItem('Blockchain Edu', 'Candidate: Sarah Khan', 9.2),
      ],
    );
  }

  Widget _buildLiveSessions() {
    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: Colors.redAccent.withOpacity(0.05),
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: Colors.redAccent.withOpacity(0.1)),
      ),
      child: Row(
        children: [
          const Icon(LucideIcons.video, color: Colors.redAccent),
          const SizedBox(width: 16),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('LIVE PITCH: ROUND 4', style: GoogleFonts.inter(fontWeight: FontWeight.bold)),
                Text('Starting in 45 minutes', style: GoogleFonts.inter(fontSize: 12, color: Colors.grey)),
              ],
            ),
          ),
          ElevatedButton(
            onPressed: () {},
            style: ElevatedButton.styleFrom(backgroundColor: Colors.redAccent, foregroundColor: Colors.white),
            child: const Text('JOIN'),
          ),
        ],
      ),
    );
  }
}

class _ScoutCard extends StatelessWidget {
  final IconData icon;
  final String label;
  final String value;
  final Color color;
  const _ScoutCard(this.icon, this.label, this.value, this.color);

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: color.withOpacity(0.1),
        borderRadius: BorderRadius.circular(20),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Icon(icon, color: color, size: 24),
          Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(value, style: GoogleFonts.inter(fontSize: 24, fontWeight: FontWeight.bold, color: color)),
              Text(label, style: GoogleFonts.inter(fontSize: 10, color: Colors.grey, fontWeight: FontWeight.bold)),
            ],
          ),
        ],
      ),
    );
  }
}

class _ReviewItem extends StatelessWidget {
  final String title;
  final String candidate;
  final double score;
  const _ReviewItem(this.title, this.candidate, this.score);

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.grey.withOpacity(0.05),
        borderRadius: BorderRadius.circular(16),
      ),
      child: Row(
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(title, style: GoogleFonts.inter(fontWeight: FontWeight.bold)),
                Text(candidate, style: GoogleFonts.inter(fontSize: 12, color: Colors.grey)),
              ],
            ),
          ),
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
            decoration: BoxDecoration(color: AppTheme.linkedinBlue.withOpacity(0.1), borderRadius: BorderRadius.circular(8)),
            child: Text(score.toString(), style: GoogleFonts.inter(fontWeight: FontWeight.bold, color: AppTheme.linkedinBlue)),
          ),
        ],
      ),
    );
  }
}
