import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:lucide_icons/lucide_icons.dart';
import 'package:animate_do/animate_do.dart';
import 'package:ecoskiller_mobile_app/core/theme/app_theme.dart';
import 'package:ecoskiller_mobile_app/features/auth/presentation/pages/login_page.dart';
import 'package:ecoskiller_mobile_app/features/auth/data/providers/auth_service.dart';
import 'package:ecoskiller_mobile_app/features/shark_tank/presentation/pages/school_shark_tank_page.dart';

class SchoolDashboardPage extends StatelessWidget {
  final Map<String, dynamic> user;

  const SchoolDashboardPage({super.key, required this.user});

  @override
  Widget build(BuildContext context) {
    bool isDark = Theme.of(context).brightness == Brightness.dark;

    return Scaffold(
      backgroundColor: isDark ? AppTheme.midnightAbyss : AppTheme.skyWhite,
      body: CustomScrollView(
        slivers: [
          _buildSliverAppBar(context),
          SliverToBoxAdapter(
            child: Padding(
              padding: const EdgeInsets.all(20.0),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  FadeInDown(
                    child: _buildHeaderCard(),
                  ),
                  const SizedBox(height: 24),
                  _buildSectionTitle('SCHOOL PERFORMANCE NODE'),
                  const SizedBox(height: 16),
                  _buildStatsRow(),
                  const SizedBox(height: 32),
                  _buildSectionTitle('ACADEMIC HEALTH'),
                  const SizedBox(height: 16),
                  _buildHealthMetrics(),
                  const SizedBox(height: 32),
                  _buildSectionTitle('INNOVATION NODES'),
                  const SizedBox(height: 16),
                  _buildSharkTankCard(context),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildSliverAppBar(BuildContext context) {
    bool isDark = Theme.of(context).brightness == Brightness.dark;
    return SliverAppBar(
      expandedHeight: 120,
      pinned: true,
      backgroundColor: isDark ? AppTheme.midnightAbyss : AppTheme.skyWhite,
      actions: [
        IconButton(
          icon: const Icon(LucideIcons.logOut, color: Colors.redAccent),
          onPressed: () async {
            await AuthService().logout();
            if (context.mounted) {
              Navigator.pushAndRemoveUntil(
                context,
                MaterialPageRoute(builder: (context) => const LoginPage()),
                (route) => false,
              );
            }
          },
        ),
      ],
      flexibleSpace: FlexibleSpaceBar(
        title: Text(
          'SCHOOL NODE',
          style: GoogleFonts.inter(
            color: isDark ? AppTheme.crispText : AppTheme.deepOcean,
            fontWeight: FontWeight.w900,
            fontSize: 16,
            letterSpacing: 2,
          ),
        ),
      ),
    );
  }

  Widget _buildHeaderCard() {
    return Container(
      padding: const EdgeInsets.all(24),
      decoration: BoxDecoration(
        gradient: const LinearGradient(
          colors: [Color(0xFF00c6ff), Color(0xFF0072ff)],
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
        ),
        borderRadius: BorderRadius.circular(24),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            'School: ${user['name']}',
            style: GoogleFonts.inter(color: Colors.white, fontSize: 18, fontWeight: FontWeight.bold),
          ),
          const SizedBox(height: 8),
          Text(
            'Laying foundations for excellence',
            style: GoogleFonts.inter(color: Colors.white70, fontSize: 12),
          ),
        ],
      ),
    );
  }

  Widget _buildSectionTitle(String title) {
    return Text(
      title,
      style: GoogleFonts.inter(
        color: AppTheme.linkedinBlue.withOpacity(0.6),
        fontSize: 11,
        fontWeight: FontWeight.w900,
        letterSpacing: 1.5,
      ),
    );
  }

  Widget _buildStatsRow() {
    return Row(
      children: [
        _buildStatCard('Enrolled', '450', Colors.lightBlue),
        const SizedBox(width: 16),
        _buildStatCard('Skill Index', '8.2', Colors.blueAccent),
      ],
    );
  }

  Widget _buildStatCard(String label, String value, Color color) {
    return Expanded(
      child: Container(
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          color: color.withOpacity(0.1),
          borderRadius: BorderRadius.circular(20),
        ),
        child: Column(
          children: [
            Text(value, style: GoogleFonts.inter(fontSize: 24, fontWeight: FontWeight.bold, color: color)),
            Text(label, style: GoogleFonts.inter(fontSize: 10, color: Colors.grey)),
          ],
        ),
      ),
    );
  }

  Widget _buildHealthMetrics() {
    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: AppTheme.linkedinBlue.withOpacity(0.05),
        borderRadius: BorderRadius.circular(24),
      ),
      child: Column(
        children: [
          _buildHealthBar('Attendance', 0.95, Colors.green),
          const SizedBox(height: 16),
          _buildHealthBar('Skill Completion', 0.72, Colors.blue),
          const SizedBox(height: 16),
          _buildHealthBar('Parent Engagement', 0.88, Colors.orange),
        ],
      ),
    );
  }

  Widget _buildHealthBar(String label, double val, Color color) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Text(label, style: GoogleFonts.inter(fontSize: 12, fontWeight: FontWeight.bold)),
            Text('${(val * 100).toInt()}%', style: GoogleFonts.inter(fontSize: 12, color: color)),
          ],
        ),
        const SizedBox(height: 8),
        LinearProgressIndicator(
          value: val,
          backgroundColor: color.withOpacity(0.1),
          valueColor: AlwaysStoppedAnimation<Color>(color),
        ),
      ],
    );
  }

  Widget _buildSharkTankCard(BuildContext context) {
    return GestureDetector(
      onTap: () {
        Navigator.push(
          context,
          MaterialPageRoute(
            builder: (context) => SchoolSharkTankPage(user: user),
          ),
        );
      },
      child: Container(
        padding: const EdgeInsets.all(20),
        decoration: BoxDecoration(
          gradient: const LinearGradient(
            colors: [Color(0xFF6a11cb), Color(0xFF2575fc)],
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
          ),
          borderRadius: BorderRadius.circular(24),
          boxShadow: [
            BoxShadow(
              color: Colors.purple.withOpacity(0.2),
              blurRadius: 15,
              offset: const Offset(0, 8),
            ),
          ],
        ),
        child: Row(
          children: [
            Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: Colors.white.withOpacity(0.1),
                shape: BoxShape.circle,
              ),
              child: const Icon(LucideIcons.flag, color: Colors.white, size: 28),
            ),
            const SizedBox(width: 20),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    'CAMPUS SHARK TANK',
                    style: GoogleFonts.inter(
                      color: Colors.white,
                      fontWeight: FontWeight.w900,
                      fontSize: 16,
                      letterSpacing: 1.5,
                    ),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    'Manage innovation rounds for students',
                    style: GoogleFonts.inter(color: Colors.white70, fontSize: 12),
                  ),
                ],
              ),
            ),
            const Icon(LucideIcons.chevronRight, color: Colors.white54),
          ],
        ),
      ),
    );
  }
}
